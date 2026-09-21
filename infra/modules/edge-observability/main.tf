locals {
  tags = {
    ManagedBy = "Terraform"
    Project   = var.name
  }

  ecs_cluster_name                = "${var.name}-cluster"
  query_target_group_arn_suffix   = split(":", var.query_target_group_arn)[5]
  command_target_group_arn_suffix = split(":", var.command_target_group_arn)[5]
  valkey_cache_cluster_id         = "${var.name}-valkey-001"
  business_api_name               = "${var.name}-api"
  business_stage_name             = "demo"

  api_invoker_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { AWS = var.trusted_principal_arns }
    }]
  })

  api_resource_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid       = "DenyUnapprovedSourceIp"
        Effect    = "Deny"
        Principal = "*"
        Action    = "execute-api:Invoke"
        Resource  = "execute-api:/*"
        Condition = { NotIpAddress = { "aws:SourceIp" = var.allowed_cidrs } }
      },
      {
        Sid    = "AllowOnlyDemoInvokerFromApprovedNetworks"
        Effect = "Allow"
        # API Gateway resource policies accept a wildcard principal here; restrict
        # the caller to the dedicated role using its request-context ARN. A role
        # ARN directly in Principal is rejected by the REST API create endpoint.
        Principal = "*"
        Action    = "execute-api:Invoke"
        Resource  = "execute-api:/*"
        Condition = { ArnEquals = { "aws:PrincipalArn" = aws_iam_role.api_invoker.arn } }
      },
    ]
  })
}

resource "aws_lb" "internal" {
  name               = "${var.name}-internal"
  internal           = true
  load_balancer_type = "application"
  security_groups    = [var.alb_security_group_id]
  subnets            = var.private_app_subnet_ids
  idle_timeout       = 30

  enable_deletion_protection = false

  tags = local.tags
}

resource "aws_lb_listener" "http" {
  load_balancer_arn = aws_lb.internal.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "fixed-response"
    fixed_response {
      content_type = "application/json"
      message_body = "{\"status\":404,\"title\":\"Not Found\"}"
      status_code  = "404"
    }
  }
}

resource "aws_lb_listener_rule" "get_event" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 10

  action {
    type             = "forward"
    target_group_arn = var.query_target_group_arn
  }

  condition {
    http_request_method { values = ["GET"] }
  }
  condition {
    path_pattern { values = ["/events/*"] }
  }
}

resource "aws_lb_listener_rule" "get_reservation" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 20

  action {
    type             = "forward"
    target_group_arn = var.query_target_group_arn
  }

  condition {
    http_request_method { values = ["GET"] }
  }
  condition {
    path_pattern { values = ["/reservations/*"] }
  }
}

resource "aws_lb_listener_rule" "create_event" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 30

  action {
    type             = "forward"
    target_group_arn = var.command_target_group_arn
  }

  condition {
    http_request_method { values = ["POST"] }
  }
  condition {
    path_pattern { values = ["/events"] }
  }
}

resource "aws_lb_listener_rule" "create_reservation" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 40

  action {
    type             = "forward"
    target_group_arn = var.command_target_group_arn
  }

  condition {
    http_request_method { values = ["POST"] }
  }
  condition {
    path_pattern { values = ["/events/*/reservations"] }
  }
}

resource "aws_lb_listener_rule" "cancel_reservation" {
  listener_arn = aws_lb_listener.http.arn
  priority     = 50

  action {
    type             = "forward"
    target_group_arn = var.command_target_group_arn
  }

  condition {
    http_request_method { values = ["DELETE"] }
  }
  condition {
    path_pattern { values = ["/reservations/*"] }
  }
}

resource "aws_apigatewayv2_vpc_link" "private" {
  name               = "${var.name}-private"
  security_group_ids = [var.vpc_link_security_group_id]
  subnet_ids         = var.private_app_subnet_ids
  tags               = local.tags
}

resource "aws_iam_role" "api_gateway_logs" {
  name = "${var.name}-api-gateway-logs"
  assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "apigateway.amazonaws.com" }
    }]
  })
  tags = local.tags
}

resource "aws_iam_role_policy_attachment" "api_gateway_logs" {
  role       = aws_iam_role.api_gateway_logs.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonAPIGatewayPushToCloudWatchLogs"
}

resource "aws_api_gateway_account" "this" {
  cloudwatch_role_arn = aws_iam_role.api_gateway_logs.arn
}

resource "aws_iam_role" "api_invoker" {
  name               = "${var.name}-ApiInvokerRole"
  assume_role_policy = local.api_invoker_assume_role_policy
  tags               = local.tags
}

resource "aws_api_gateway_rest_api" "this" {
  name        = "${var.name}-api"
  description = "IAM-authenticated private integration entrypoint for ${var.name}"
  endpoint_configuration { types = ["REGIONAL"] }

  policy = local.api_resource_policy

  tags = local.tags
}

resource "aws_iam_role_policy" "api_invoker" {
  name = "invoke-demo-api-only"
  role = aws_iam_role.api_invoker.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "execute-api:Invoke"
      Resource = "${aws_api_gateway_rest_api.this.execution_arn}/*"
    }]
  })
}

resource "aws_api_gateway_resource" "events" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_rest_api.this.root_resource_id
  path_part   = "events"
}

resource "aws_api_gateway_resource" "event_id" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.events.id
  path_part   = "{id}"
}

resource "aws_api_gateway_resource" "event_reservations" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.event_id.id
  path_part   = "reservations"
}

resource "aws_api_gateway_resource" "reservations" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_rest_api.this.root_resource_id
  path_part   = "reservations"
}

resource "aws_api_gateway_resource" "reservation_id" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  parent_id   = aws_api_gateway_resource.reservations.id
  path_part   = "{id}"
}

resource "aws_api_gateway_method" "create_event" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  resource_id   = aws_api_gateway_resource.events.id
  http_method   = "POST"
  authorization = "AWS_IAM"
}

resource "aws_api_gateway_method" "get_event" {
  rest_api_id        = aws_api_gateway_rest_api.this.id
  resource_id        = aws_api_gateway_resource.event_id.id
  http_method        = "GET"
  authorization      = "AWS_IAM"
  request_parameters = { "method.request.path.id" = true }
}

resource "aws_api_gateway_method" "create_reservation" {
  rest_api_id        = aws_api_gateway_rest_api.this.id
  resource_id        = aws_api_gateway_resource.event_reservations.id
  http_method        = "POST"
  authorization      = "AWS_IAM"
  request_parameters = { "method.request.path.id" = true }
}

resource "aws_api_gateway_method" "get_reservation" {
  rest_api_id        = aws_api_gateway_rest_api.this.id
  resource_id        = aws_api_gateway_resource.reservation_id.id
  http_method        = "GET"
  authorization      = "AWS_IAM"
  request_parameters = { "method.request.path.id" = true }
}

resource "aws_api_gateway_method" "cancel_reservation" {
  rest_api_id        = aws_api_gateway_rest_api.this.id
  resource_id        = aws_api_gateway_resource.reservation_id.id
  http_method        = "DELETE"
  authorization      = "AWS_IAM"
  request_parameters = { "method.request.path.id" = true }
}

resource "aws_api_gateway_integration" "create_event" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.events.id
  http_method             = aws_api_gateway_method.create_event.http_method
  integration_http_method = "POST"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.private.id
  integration_target      = aws_lb.internal.arn
  uri                     = "http://${aws_lb.internal.dns_name}/events"
}

resource "aws_api_gateway_integration" "get_event" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.event_id.id
  http_method             = aws_api_gateway_method.get_event.http_method
  integration_http_method = "GET"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.private.id
  integration_target      = aws_lb.internal.arn
  uri                     = "http://${aws_lb.internal.dns_name}/events/{id}"
  request_parameters      = { "integration.request.path.id" = "method.request.path.id" }
}

resource "aws_api_gateway_integration" "create_reservation" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.event_reservations.id
  http_method             = aws_api_gateway_method.create_reservation.http_method
  integration_http_method = "POST"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.private.id
  integration_target      = aws_lb.internal.arn
  uri                     = "http://${aws_lb.internal.dns_name}/events/{id}/reservations"
  request_parameters      = { "integration.request.path.id" = "method.request.path.id" }
}

resource "aws_api_gateway_integration" "get_reservation" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.reservation_id.id
  http_method             = aws_api_gateway_method.get_reservation.http_method
  integration_http_method = "GET"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.private.id
  integration_target      = aws_lb.internal.arn
  uri                     = "http://${aws_lb.internal.dns_name}/reservations/{id}"
  request_parameters      = { "integration.request.path.id" = "method.request.path.id" }
}

resource "aws_api_gateway_integration" "cancel_reservation" {
  rest_api_id             = aws_api_gateway_rest_api.this.id
  resource_id             = aws_api_gateway_resource.reservation_id.id
  http_method             = aws_api_gateway_method.cancel_reservation.http_method
  integration_http_method = "DELETE"
  type                    = "HTTP_PROXY"
  connection_type         = "VPC_LINK"
  connection_id           = aws_apigatewayv2_vpc_link.private.id
  integration_target      = aws_lb.internal.arn
  uri                     = "http://${aws_lb.internal.dns_name}/reservations/{id}"
  request_parameters      = { "integration.request.path.id" = "method.request.path.id" }
}

resource "aws_cloudwatch_log_group" "api_access" {
  name              = "/apigateway/${var.name}/access"
  retention_in_days = 7
  tags              = local.tags
}

resource "aws_api_gateway_deployment" "this" {
  rest_api_id = aws_api_gateway_rest_api.this.id

  triggers = {
    redeployment = sha1(jsonencode([
      aws_api_gateway_integration.create_event.id,
      aws_api_gateway_integration.get_event.id,
      aws_api_gateway_integration.create_reservation.id,
      aws_api_gateway_integration.get_reservation.id,
      aws_api_gateway_integration.cancel_reservation.id
    ]))
    api_policy = local.api_resource_policy
  }

  lifecycle { create_before_destroy = true }
}

resource "aws_api_gateway_stage" "demo" {
  rest_api_id   = aws_api_gateway_rest_api.this.id
  deployment_id = aws_api_gateway_deployment.this.id
  stage_name    = "demo"

  access_log_settings {
    destination_arn = aws_cloudwatch_log_group.api_access.arn
    format = jsonencode({
      requestId       = "$context.requestId"
      status          = "$context.status"
      integration     = "$context.integration.status"
      responseLatency = "$context.responseLatency"
      sourceIp        = "$context.identity.sourceIp"
    })
  }

  tags = local.tags

  depends_on = [aws_api_gateway_account.this]
}

resource "aws_api_gateway_method_settings" "get_event" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.demo.stage_name
  method_path = "events/{id}/GET"
  settings {
    metrics_enabled        = true
    logging_level          = "INFO"
    data_trace_enabled     = false
    throttling_burst_limit = 40
    throttling_rate_limit  = 20
  }
}

resource "aws_api_gateway_method_settings" "get_reservation" {
  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.demo.stage_name
  method_path = "reservations/{id}/GET"
  settings {
    metrics_enabled        = true
    logging_level          = "INFO"
    data_trace_enabled     = false
    throttling_burst_limit = 40
    throttling_rate_limit  = 20
  }
}

resource "aws_api_gateway_method_settings" "commands" {
  for_each = toset(["events/POST", "events/{id}/reservations/POST", "reservations/{id}/DELETE"])

  rest_api_id = aws_api_gateway_rest_api.this.id
  stage_name  = aws_api_gateway_stage.demo.stage_name
  method_path = each.value
  settings {
    metrics_enabled        = true
    logging_level          = "INFO"
    data_trace_enabled     = false
    throttling_burst_limit = 10
    throttling_rate_limit  = 5
  }
}

resource "aws_wafv2_web_acl" "api" {
  name  = "${var.name}-api"
  scope = "REGIONAL"

  default_action {
    allow {}
  }

  rule {
    name     = "RateLimit"
    priority = 1

    action {
      block {}
    }

    statement {
      rate_based_statement {
        limit              = var.waf_rate_limit
        aggregate_key_type = "IP"
      }
    }

    visibility_config {
      cloudwatch_metrics_enabled = true
      metric_name                = "${var.name}-rate-limit"
      sampled_requests_enabled   = true
    }
  }

  visibility_config {
    cloudwatch_metrics_enabled = true
    metric_name                = "${var.name}-api"
    sampled_requests_enabled   = true
  }

  tags = local.tags
}

resource "aws_wafv2_web_acl_association" "api" {
  resource_arn = aws_api_gateway_stage.demo.arn
  web_acl_arn  = aws_wafv2_web_acl.api.arn
}

resource "aws_budgets_budget" "demo" {
  name         = "${var.name}-monthly-cap"
  budget_type  = "COST"
  limit_amount = "5"
  limit_unit   = "USD"
  time_unit    = "MONTHLY"

  dynamic "notification" {
    for_each = toset([50, 80, 100])
    content {
      comparison_operator        = "GREATER_THAN"
      threshold                  = notification.value
      threshold_type             = "PERCENTAGE"
      notification_type          = "ACTUAL"
      subscriber_email_addresses = [var.budget_alert_email]
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "api_5xx" {
  alarm_name          = "${var.name}-api-5xx"
  alarm_description   = "API Gateway returned server errors."
  namespace           = "AWS/ApiGateway"
  metric_name         = "5XXError"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  alarm_actions       = compact([var.alarm_topic_arn])
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ApiName = aws_api_gateway_rest_api.this.name
    Stage   = aws_api_gateway_stage.demo.stage_name
  }
}

resource "aws_cloudwatch_metric_alarm" "api_4xx" {
  alarm_name          = "${var.name}-api-4xx"
  alarm_description   = "API Gateway returned client errors, including throttling responses."
  namespace           = "AWS/ApiGateway"
  metric_name         = "4XXError"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  alarm_actions       = compact([var.alarm_topic_arn])
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ApiName = aws_api_gateway_rest_api.this.name
    Stage   = aws_api_gateway_stage.demo.stage_name
  }
}

resource "aws_cloudwatch_metric_alarm" "unhealthy_targets" {
  alarm_name          = "${var.name}-unhealthy-targets"
  alarm_description   = "The internal ALB has unhealthy ECS targets."
  namespace           = "AWS/ApplicationELB"
  metric_name         = "UnHealthyHostCount"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  alarm_actions       = compact([var.alarm_topic_arn])
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    LoadBalancer = aws_lb.internal.arn_suffix
  }
}

resource "aws_cloudwatch_dashboard" "demo" {
  dashboard_name = "${var.name}-demo"
  dashboard_body = jsonencode({
    widgets = [
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Edge & API\nDemanda, erros e caminho interno até os serviços."
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "API Gateway requests and errors"
          region   = var.aws_region
          stat     = "Sum"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", aws_api_gateway_rest_api.this.name, "Stage", aws_api_gateway_stage.demo.stage_name, { label = "Requests", color = "#2ca02c" }],
            [".", "4XXError", ".", ".", ".", ".", { label = "4xx", color = "#ff7f0e" }],
            [".", "5XXError", ".", ".", ".", ".", { label = "5xx", color = "#d62728" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "API Gateway latency percentiles"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ApiGateway", "Latency", "ApiName", aws_api_gateway_rest_api.this.name, "Stage", aws_api_gateway_stage.demo.stage_name, { label = "Latency p50", stat = "p50", color = "#1f77b4" }],
            [".", "Latency", ".", ".", ".", ".", { label = "Latency p95", stat = "p95", color = "#ff7f0e" }],
            [".", "Latency", ".", ".", ".", ".", { label = "Latency p99", stat = "p99", color = "#d62728" }],
            [".", "IntegrationLatency", ".", ".", ".", ".", { label = "Integration p95", stat = "p95", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Internal ALB target health"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", local.query_target_group_arn_suffix, "LoadBalancer", aws_lb.internal.arn_suffix, { label = "query healthy", stat = "Minimum", color = "#2ca02c" }],
            [".", "UnHealthyHostCount", ".", ".", ".", ".", { label = "query unhealthy", stat = "Maximum", color = "#d62728" }],
            ["AWS/ApplicationELB", "HealthyHostCount", "TargetGroup", local.command_target_group_arn_suffix, "LoadBalancer", aws_lb.internal.arn_suffix, { label = "command healthy", stat = "Minimum", color = "#17becf" }],
            [".", "UnHealthyHostCount", ".", ".", ".", ".", { label = "command unhealthy", stat = "Maximum", color = "#ff9896" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Internal ALB target latency"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", local.query_target_group_arn_suffix, "LoadBalancer", aws_lb.internal.arn_suffix, { label = "query p50", stat = "p50", color = "#1f77b4" }],
            [".", "TargetResponseTime", ".", ".", ".", ".", { label = "query p95", stat = "p95", color = "#ff7f0e" }],
            ["AWS/ApplicationELB", "TargetResponseTime", "TargetGroup", local.command_target_group_arn_suffix, "LoadBalancer", aws_lb.internal.arn_suffix, { label = "command p50", stat = "p50", color = "#17becf" }],
            [".", "TargetResponseTime", ".", ".", ".", ".", { label = "command p95", stat = "p95", color = "#d62728" }],
          ]
        }
      },
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Runtime ECS\nCapacidade desejada versus disponível e saturação por serviço."
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "ECS desired and running tasks"
          region   = var.aws_region
          stat     = "Average"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["ECS/ContainerInsights", "RunningTaskCount", "ServiceName", "query-api", "ClusterName", local.ecs_cluster_name, { label = "query running", color = "#1f77b4" }],
            [".", "DesiredTaskCount", ".", ".", ".", ".", { label = "query desired", color = "#aec7e8" }],
            ["ECS/ContainerInsights", "RunningTaskCount", "ServiceName", "command-api", "ClusterName", local.ecs_cluster_name, { label = "command running", color = "#ff7f0e" }],
            [".", "DesiredTaskCount", ".", ".", ".", ".", { label = "command desired", color = "#ffbb78" }],
            ["ECS/ContainerInsights", "RunningTaskCount", "ServiceName", "worker", "ClusterName", local.ecs_cluster_name, { label = "worker running", color = "#2ca02c" }],
            [".", "DesiredTaskCount", ".", ".", ".", ".", { label = "worker desired", color = "#98df8a" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "ECS CPU utilization"
          region   = var.aws_region
          stat     = "Average"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", local.ecs_cluster_name, "ServiceName", "query-api", { label = "query-api", color = "#1f77b4" }],
            [".", "CPUUtilization", ".", ".", ".", "command-api", { label = "command-api", color = "#ff7f0e" }],
            [".", "CPUUtilization", ".", ".", ".", "worker", { label = "worker", color = "#2ca02c" }],
          ]
          yAxis = {
            left = { min = 0, max = 100, label = "Percent" }
          }
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "ECS memory utilization"
          region   = var.aws_region
          stat     = "Average"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ECS", "MemoryUtilization", "ClusterName", local.ecs_cluster_name, "ServiceName", "query-api", { label = "query-api", color = "#1f77b4" }],
            [".", "MemoryUtilization", ".", ".", ".", "command-api", { label = "command-api", color = "#ff7f0e" }],
            [".", "MemoryUtilization", ".", ".", ".", "worker", { label = "worker", color = "#2ca02c" }],
          ]
          yAxis = {
            left = { min = 0, max = 100, label = "Percent" }
          }
        }
      },
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Data & async\nPostgreSQL, Valkey e filas que sustentam reservas e notificações."
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "RDS CPU and connections"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", "${var.name}-postgres", { label = "CPU", stat = "Average", color = "#1f77b4" }],
            [".", "DatabaseConnections", ".", ".", { label = "Connections", stat = "Average", yAxis = "right", color = "#ff7f0e" }],
          ]
          yAxis = {
            left  = { min = 0, max = 100, label = "Percent" }
            right = { min = 0, label = "Connections" }
          }
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "RDS memory and storage headroom"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/RDS", "FreeableMemory", "DBInstanceIdentifier", "${var.name}-postgres", { label = "Freeable memory", stat = "Minimum", color = "#2ca02c" }],
            [".", "FreeStorageSpace", ".", ".", { label = "Free storage", stat = "Minimum", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Valkey saturation and connections"
          region   = var.aws_region
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ElastiCache", "EngineCPUUtilization", "CacheClusterId", local.valkey_cache_cluster_id, { label = "Engine CPU", stat = "Average", color = "#1f77b4" }],
            ["AWS/ElastiCache", "DatabaseMemoryUsageCountedForEvictPercentage", "ReplicationGroupId", "${var.name}-valkey", { label = "Memory used", stat = "Average", color = "#d62728" }],
            ["AWS/ElastiCache", "CurrConnections", "CacheClusterId", local.valkey_cache_cluster_id, { label = "Connections", stat = "Average", yAxis = "right", color = "#ff7f0e" }],
          ]
          yAxis = {
            left  = { min = 0, max = 100, label = "Percent" }
            right = { min = 0, label = "Connections" }
          }
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Valkey cache activity"
          region   = var.aws_region
          stat     = "Sum"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/ElastiCache", "CacheHits", "CacheClusterId", local.valkey_cache_cluster_id, { label = "Hits", color = "#2ca02c" }],
            [".", "CacheMisses", ".", ".", { label = "Misses", color = "#d62728" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "SQS backlog and DLQs"
          region   = var.aws_region
          stat     = "Maximum"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/SQS", "ApproximateNumberOfMessagesVisible", "QueueName", "${var.name}-expiration", { label = "expiration", color = "#1f77b4" }],
            [".", "ApproximateNumberOfMessagesVisible", ".", "${var.name}-notification", { label = "notification", color = "#2ca02c" }],
            [".", "ApproximateNumberOfMessagesVisible", ".", "${var.name}-expiration-dlq", { label = "expiration DLQ", color = "#d62728" }],
            [".", "ApproximateNumberOfMessagesVisible", ".", "${var.name}-notification-dlq", { label = "notification DLQ", color = "#ff9896" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "SQS oldest message age"
          region   = var.aws_region
          stat     = "Maximum"
          period   = 60
          view     = "timeSeries"
          stacked  = false
          liveData = true
          metrics = [
            ["AWS/SQS", "ApproximateAgeOfOldestMessage", "QueueName", "${var.name}-expiration", { label = "expiration", color = "#1f77b4" }],
            [".", "ApproximateAgeOfOldestMessage", ".", "${var.name}-notification", { label = "notification", color = "#2ca02c" }],
          ]
          yAxis = {
            left = { min = 0, label = "Seconds" }
          }
        }
      },
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Recent failures\nConsultas sob demanda nos log groups existentes; reduza a janela para limitar a varredura."
        }
      },
      {
        type   = "log"
        width  = 12
        height = 6
        properties = {
          title  = "Recent API Gateway 4xx/5xx"
          region = var.aws_region
          view   = "table"
          query  = "SOURCE '/apigateway/${var.name}/access' | fields @timestamp, requestId, status, integration, responseLatency, sourceIp | filter status >= 400 | sort @timestamp desc | limit 50"
        }
      },
      {
        type   = "log"
        width  = 12
        height = 6
        properties = {
          title  = "Recent application errors"
          region = var.aws_region
          view   = "table"
          query  = "SOURCE '/ecs/${var.name}/query-api' | SOURCE '/ecs/${var.name}/command-api' | SOURCE '/ecs/${var.name}/worker' | fields @timestamp, @log, @logStream, @message | filter @message like /ERROR|Exception|Caused by/ | sort @timestamp desc | limit 50"
        }
      }
    ]
  })
}

resource "aws_cloudwatch_dashboard" "business" {
  dashboard_name = "${var.name}-negocio"
  dashboard_body = jsonencode({
    start          = "-PT24H"
    periodOverride = "inherit"
    widgets = [
      {
        type   = "text"
        width  = 24
        height = 3
        properties = {
          markdown = "# Visão de negócio\nAcompanhe interesse, aceite e experiência na jornada de reserva.\n\n**Como interpretar:** os números representam interações e respostas da aplicação, não clientes únicos, reservas únicas, vendas ou receita. Repetições idempotentes podem aparecer novamente."
        }
      },
      {
        type   = "metric"
        width  = 6
        height = 5
        properties = {
          title                = "Eventos publicados"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events", "Stage", local.business_stage_name, "Method", "POST", { id = "eventos_total", label = "Total de respostas", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "eventos_4xx", label = "Não concluídas por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "eventos_5xx", label = "Falhas técnicas", stat = "Sum", visible = false }],
            [{ expression = "eventos_total-eventos_4xx-eventos_5xx", id = "eventos_publicados", label = "Eventos publicados", color = "#2ca02c" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 6
        height = 5
        properties = {
          title                = "Consultas de evento concluídas"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}", "Stage", local.business_stage_name, "Method", "GET", { id = "consultas_total", label = "Total de respostas", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "consultas_4xx", label = "Não concluídas por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "consultas_5xx", label = "Falhas técnicas", stat = "Sum", visible = false }],
            [{ expression = "consultas_total-consultas_4xx-consultas_5xx", id = "consultas_concluidas", label = "Consultas concluídas", color = "#1f77b4" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 6
        height = 5
        properties = {
          title                = "Respostas de reserva aceitas"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { id = "reservas_total", label = "Tentativas de reserva", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "reservas_4xx", label = "Não concluídas por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "reservas_5xx", label = "Falhas técnicas", stat = "Sum", visible = false }],
            [{ expression = "reservas_total-reservas_4xx-reservas_5xx", id = "reservas_aceitas", label = "Respostas aceitas", color = "#2ca02c" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 6
        height = 5
        properties = {
          title                = "Cancelamentos concluídos"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "DELETE", { id = "cancelamentos_total", label = "Solicitações de cancelamento", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "cancelamentos_4xx", label = "Não concluídas por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "cancelamentos_5xx", label = "Falhas técnicas", stat = "Sum", visible = false }],
            [{ expression = "cancelamentos_total-cancelamentos_4xx-cancelamentos_5xx", id = "cancelamentos_concluidos", label = "Cancelamentos concluídos", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Jornada do cliente"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Interações por etapa"
          region   = var.aws_region
          view     = "timeSeries"
          period   = 300
          stat     = "Sum"
          stacked  = false
          liveData = true
          legend   = { position = "bottom" }
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}", "Stage", local.business_stage_name, "Method", "GET", { label = "Consultas de evento", color = "#1f77b4" }],
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { label = "Tentativas de reserva", color = "#ff7f0e" }],
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "GET", { label = "Consultas da reserva", color = "#2ca02c" }],
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "DELETE", { label = "Solicitações de cancelamento", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Resultados das tentativas de reserva"
          region   = var.aws_region
          view     = "timeSeries"
          period   = 300
          stacked  = true
          liveData = true
          legend   = { position = "bottom" }
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { id = "tentativas", label = "Tentativas de reserva", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "nao_concluidas", label = "Não concluídas por regra ou entrada", stat = "Sum", color = "#ff7f0e" }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "falhas_tecnicas", label = "Falhas técnicas", stat = "Sum", color = "#d62728" }],
            [{ expression = "tentativas-nao_concluidas-falhas_tecnicas", id = "aceitas", label = "Respostas aceitas", color = "#2ca02c" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 8
        height = 5
        properties = {
          title                = "Taxa de aceite de reservas"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { id = "taxa_tentativas", label = "Tentativas de reserva", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "taxa_4xx", label = "Não concluídas por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "taxa_5xx", label = "Falhas técnicas", stat = "Sum", visible = false }],
            [{ expression = "IF(taxa_tentativas>0,100*(taxa_tentativas-taxa_4xx-taxa_5xx)/taxa_tentativas,0)", id = "taxa_aceite", label = "Taxa de aceite (%)", color = "#2ca02c" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 8
        height = 5
        properties = {
          title    = "Interações não concluídas por etapa"
          region   = var.aws_region
          view     = "timeSeries"
          period   = 300
          stacked  = false
          liveData = true
          legend   = { position = "bottom" }
          metrics = [
            ["AWS/ApiGateway", "4XXError", "ApiName", local.business_api_name, "Resource", "/events/{id}", "Stage", local.business_stage_name, "Method", "GET", { id = "consulta_4xx", label = "Consulta: regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "consulta_5xx", label = "Consulta: falha técnica", stat = "Sum", visible = false }],
            ["AWS/ApiGateway", "4XXError", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { id = "reserva_4xx", label = "Reserva: regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "reserva_5xx", label = "Reserva: falha técnica", stat = "Sum", visible = false }],
            ["AWS/ApiGateway", "4XXError", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "DELETE", { id = "cancelamento_4xx", label = "Cancelamento: regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "cancelamento_5xx", label = "Cancelamento: falha técnica", stat = "Sum", visible = false }],
            [{ expression = "consulta_4xx+consulta_5xx", id = "consulta_nao_concluida", label = "Consultas de evento", color = "#1f77b4" }],
            [{ expression = "reserva_4xx+reserva_5xx", id = "reserva_nao_concluida", label = "Tentativas de reserva", color = "#ff7f0e" }],
            [{ expression = "cancelamento_4xx+cancelamento_5xx", id = "cancelamento_nao_concluido", label = "Solicitações de cancelamento", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "metric"
        width  = 8
        height = 5
        properties = {
          title                = "Acompanhamento após a reserva"
          region               = var.aws_region
          view                 = "singleValue"
          period               = 300
          setPeriodToTimeRange = true
          metrics = [
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "GET", { id = "acompanhamento_total", label = "Consultas da reserva", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "acompanhamento_4xx", label = "Consulta não concluída por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "acompanhamento_5xx", label = "Consulta com falha técnica", stat = "Sum", visible = false }],
            ["AWS/ApiGateway", "Count", "ApiName", local.business_api_name, "Resource", "/reservations/{id}", "Stage", local.business_stage_name, "Method", "DELETE", { id = "encerramento_total", label = "Solicitações de cancelamento", stat = "Sum", visible = false }],
            [".", "4XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "encerramento_4xx", label = "Cancelamento não concluído por regra ou entrada", stat = "Sum", visible = false }],
            [".", "5XXError", ".", ".", ".", ".", ".", ".", ".", ".", { id = "encerramento_5xx", label = "Cancelamento com falha técnica", stat = "Sum", visible = false }],
            [{ expression = "acompanhamento_total-acompanhamento_4xx-acompanhamento_5xx", id = "consultas_reserva", label = "Consultas da reserva concluídas", color = "#1f77b4" }],
            [{ expression = "encerramento_total-encerramento_4xx-encerramento_5xx", id = "cancelamentos_finais", label = "Cancelamentos concluídos", color = "#9467bd" }],
          ]
        }
      },
      {
        type   = "text"
        width  = 24
        height = 1
        properties = {
          markdown = "# Experiência percebida"
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Tempo para consultar um evento"
          region   = var.aws_region
          view     = "timeSeries"
          period   = 300
          stacked  = false
          liveData = true
          legend   = { position = "bottom" }
          metrics = [
            ["AWS/ApiGateway", "Latency", "ApiName", local.business_api_name, "Resource", "/events/{id}", "Stage", local.business_stage_name, "Method", "GET", { label = "Mediana (p50)", stat = "p50", color = "#1f77b4" }],
            [".", "Latency", ".", ".", ".", ".", ".", ".", ".", ".", { label = "95% das respostas (p95)", stat = "p95", color = "#ff7f0e" }],
          ]
          yAxis = {
            left = { min = 0, label = "Milissegundos" }
          }
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title    = "Tempo para tentar uma reserva"
          region   = var.aws_region
          view     = "timeSeries"
          period   = 300
          stacked  = false
          liveData = true
          legend   = { position = "bottom" }
          metrics = [
            ["AWS/ApiGateway", "Latency", "ApiName", local.business_api_name, "Resource", "/events/{id}/reservations", "Stage", local.business_stage_name, "Method", "POST", { label = "Mediana (p50)", stat = "p50", color = "#1f77b4" }],
            [".", "Latency", ".", ".", ".", ".", ".", ".", ".", ".", { label = "95% das respostas (p95)", stat = "p95", color = "#ff7f0e" }],
          ]
          yAxis = {
            left = { min = 0, label = "Milissegundos" }
          }
        }
      },
    ]
  })
}
