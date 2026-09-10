locals {
  tags = {
    ManagedBy = "Terraform"
    Project   = var.name
  }

  api_invoker_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { AWS = var.trusted_principal_arns }
    }]
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

  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Sid    = "AllowOnlyDemoInvokerFromApprovedNetworks"
      Effect = "Allow"
      # API Gateway resource policies accept a wildcard principal here; restrict
      # the caller to the dedicated role using its request-context ARN.  A role
      # ARN directly in Principal is rejected by the REST API create endpoint.
      Principal = "*"
      Action    = "execute-api:Invoke"
      Resource  = "execute-api:/*"
      Condition = {
        IpAddress = { "aws:SourceIp" = var.allowed_cidrs }
        ArnEquals = { "aws:PrincipalArn" = aws_iam_role.api_invoker.arn }
      }
    }]
  })

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
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  dimensions = {
    ApiName = aws_api_gateway_rest_api.this.name
    Stage   = aws_api_gateway_stage.demo.stage_name
  }
}

resource "aws_cloudwatch_metric_alarm" "api_throttle" {
  alarm_name          = "${var.name}-api-throttle"
  alarm_description   = "API Gateway throttled requests."
  namespace           = "AWS/ApiGateway"
  metric_name         = "Throttle"
  statistic           = "Sum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
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
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "API Gateway errors and throttles"
          region  = var.aws_region
          stat    = "Sum"
          period  = 60
          metrics = [["AWS/ApiGateway", "5XXError", "ApiName", aws_api_gateway_rest_api.this.name, "Stage", aws_api_gateway_stage.demo.stage_name], [".", "Throttle", ".", ".", ".", "."]]
        }
      },
      {
        type   = "metric"
        width  = 12
        height = 6
        properties = {
          title   = "Internal ALB unhealthy targets"
          region  = var.aws_region
          stat    = "Maximum"
          period  = 60
          metrics = [["AWS/ApplicationELB", "UnHealthyHostCount", "LoadBalancer", aws_lb.internal.arn_suffix]]
        }
      }
    ]
  })
}
