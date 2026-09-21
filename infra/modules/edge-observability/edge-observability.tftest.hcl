mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/mock" }
  }
  mock_resource "aws_lb" {
    defaults = {
      arn        = "arn:aws:elasticloadbalancing:sa-east-1:123456789012:loadbalancer/app/mock/1234567890abcdef"
      arn_suffix = "app/mock/1234567890abcdef"
      dns_name   = "internal-mock-123.sa-east-1.elb.amazonaws.com"
    }
  }
  mock_resource "aws_lb_listener" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:sa-east-1:123456789012:listener/app/mock/1234567890abcdef/1234567890abcdef"
    }
  }
  mock_resource "aws_cloudwatch_log_group" {
    defaults = {
      arn = "arn:aws:logs:sa-east-1:123456789012:log-group:mock"
    }
  }
  mock_resource "aws_wafv2_web_acl" {
    defaults = {
      arn = "arn:aws:wafv2:sa-east-1:123456789012:regional/webacl/mock/12345678-1234-1234-1234-123456789012"
    }
  }
  mock_resource "aws_api_gateway_rest_api" {
    defaults = {
      id               = "mockapi"
      root_resource_id = "root"
      execution_arn    = "arn:aws:execute-api:sa-east-1:123456789012:mockapi"
    }
  }
  mock_resource "aws_api_gateway_stage" {
    defaults = { arn = "arn:aws:apigateway:sa-east-1::/restapis/mockapi/stages/demo" }
  }
  mock_resource "aws_lambda_function" {
    defaults = { arn = "arn:aws:lambda:sa-east-1:123456789012:function:mock" }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
}

run "keeps_the_api_iam_authenticated_private_and_cost_limited" {
  command = apply

  variables {
    name                       = "flash-booking-demo"
    aws_region                 = "sa-east-1"
    vpc_id                     = "vpc-123"
    private_app_subnet_ids     = ["subnet-app-a", "subnet-app-b"]
    vpc_link_security_group_id = "sg-vpclink"
    alb_security_group_id      = "sg-alb"
    query_target_group_arn     = "arn:aws:elasticloadbalancing:sa-east-1:123456789012:targetgroup/query/1234567890abcdef"
    command_target_group_arn   = "arn:aws:elasticloadbalancing:sa-east-1:123456789012:targetgroup/command/1234567890abcdef"
    trusted_principal_arns     = ["arn:aws:iam::123456789012:role/operator"]
    allowed_cidrs              = ["203.0.113.10/32"]
    budget_alert_email         = "alerts@example.com"
    budget_emergency_topic_arn = "arn:aws:sns:sa-east-1:123456789012:budget-emergency"
    ecs_cluster_arn            = "arn:aws:ecs:sa-east-1:123456789012:cluster/flash-booking-demo-cluster"
    ecs_cluster_name           = "flash-booking-demo-cluster"
    ecs_service_names          = ["query-api", "command-api", "worker"]
    ecs_service_arns           = ["arn:aws:ecs:sa-east-1:123456789012:service/flash-booking-demo-cluster/query-api", "arn:aws:ecs:sa-east-1:123456789012:service/flash-booking-demo-cluster/command-api", "arn:aws:ecs:sa-east-1:123456789012:service/flash-booking-demo-cluster/worker"]
    ecs_scalable_target_arns   = ["arn:aws:application-autoscaling:sa-east-1:123456789012:scalable-target/query", "arn:aws:application-autoscaling:sa-east-1:123456789012:scalable-target/command"]
    database_identifier        = "flash-booking-demo-postgres"
    database_arn               = "arn:aws:rds:sa-east-1:123456789012:db:flash-booking-demo-postgres"
    alarm_topic_arn            = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = aws_lb.internal.internal && aws_api_gateway_method.create_event.authorization == "AWS_IAM" && aws_api_gateway_method.get_event.authorization == "AWS_IAM" && aws_api_gateway_method.create_reservation.authorization == "AWS_IAM" && aws_api_gateway_method.get_reservation.authorization == "AWS_IAM" && aws_api_gateway_method.cancel_reservation.authorization == "AWS_IAM"
    error_message = "The public API must require IAM while the ALB remains internal."
  }

  assert {
    condition     = aws_api_gateway_integration.get_event.connection_id == aws_apigatewayv2_vpc_link.private.id && aws_api_gateway_integration.create_reservation.connection_id == aws_apigatewayv2_vpc_link.private.id && aws_api_gateway_integration.create_event.integration_target == aws_lb.internal.arn && aws_api_gateway_integration.get_event.integration_target == aws_lb.internal.arn && aws_api_gateway_integration.create_reservation.integration_target == aws_lb.internal.arn && aws_api_gateway_integration.get_reservation.integration_target == aws_lb.internal.arn && aws_api_gateway_integration.cancel_reservation.integration_target == aws_lb.internal.arn && contains(aws_apigatewayv2_vpc_link.private.security_group_ids, var.vpc_link_security_group_id)
    error_message = "API Gateway must use VPC Link V2 to reach only the internal ALB."
  }

  assert {
    condition     = aws_lb_listener_rule.get_event.action[0].target_group_arn == var.query_target_group_arn && aws_lb_listener_rule.create_event.action[0].target_group_arn == var.command_target_group_arn && aws_lb_listener_rule.cancel_reservation.action[0].target_group_arn == var.command_target_group_arn
    error_message = "GET routes must reach query-api, while POST and DELETE routes reach command-api."
  }

  assert {
    condition     = one([for rule in aws_wafv2_web_acl.api.rule : rule if rule.name == "RateLimit"]).statement[0].rate_based_statement[0].limit == var.waf_rate_limit && aws_budgets_budget.demo.limit_amount == "50" && length(aws_budgets_budget.demo.notification) == 3
    error_message = "The edge must rate-limit requests and create all three notifications for the US$50 budget."
  }

  assert {
    condition     = aws_lambda_permission.budget_emergency.principal == "sns.amazonaws.com" && aws_lambda_permission.budget_emergency.source_arn == var.budget_emergency_topic_arn && aws_sns_topic_subscription.budget_emergency.protocol == "lambda" && aws_sns_topic_subscription.budget_emergency.topic_arn == var.budget_emergency_topic_arn
    error_message = "Only the dedicated Budget topic must invoke the emergency-stop Lambda."
  }

  assert {
    condition = (
      aws_lambda_function.cost_emergency_stop.handler == "cost_emergency_stop.handler" &&
      aws_lambda_function.cost_emergency_stop.environment[0].variables.ECS_CLUSTER_ARN == var.ecs_cluster_arn &&
      aws_lambda_function.cost_emergency_stop.environment[0].variables.ECS_CLUSTER_NAME == var.ecs_cluster_name &&
      toset(jsondecode(aws_lambda_function.cost_emergency_stop.environment[0].variables.ECS_SERVICE_NAMES)) == toset(var.ecs_service_names) &&
      aws_lambda_function.cost_emergency_stop.environment[0].variables.DATABASE_IDENTIFIER == var.database_identifier
    )
    error_message = "The emergency-stop Lambda must receive exactly the demo cluster, three services, and database identifier."
  }

  assert {
    condition = (
      toset(one([for statement in jsondecode(aws_iam_role_policy.cost_emergency_stop.policy).Statement : statement if statement.Action == "ecs:UpdateService"]).Resource) == toset(var.ecs_service_arns) &&
      toset(one([for statement in jsondecode(aws_iam_role_policy.cost_emergency_stop.policy).Statement : statement if statement.Action == "application-autoscaling:RegisterScalableTarget"]).Resource) == toset(var.ecs_scalable_target_arns) &&
      one([for statement in jsondecode(aws_iam_role_policy.cost_emergency_stop.policy).Statement : statement if statement.Action == "rds:StopDBInstance"]).Resource == var.database_arn
    )
    error_message = "The emergency-stop role must limit ECS, autoscaling, and RDS writes to the demo resources."
  }

  assert {
    condition     = contains(one([for notification in aws_budgets_budget.demo.notification : notification if notification.threshold == 100]).subscriber_sns_topic_arns, var.budget_emergency_topic_arn)
    error_message = "The actual-spend 100% budget notification must publish to the dedicated emergency topic."
  }

  assert {
    condition     = jsondecode(aws_sns_topic_policy.budget_emergency.policy).Statement[0].Principal.Service == "budgets.amazonaws.com" && jsondecode(aws_sns_topic_policy.budget_emergency.policy).Statement[0].Condition.StringEquals["aws:SourceAccount"] == "123456789012"
    error_message = "Only same-account AWS Budgets may publish the emergency trigger."
  }

  assert {
    condition     = jsondecode(aws_iam_role_policy.api_invoker.policy).Statement[0].Action == "execute-api:Invoke"
    error_message = "ApiInvokerRole must grant only API invocation and never infrastructure provisioning."
  }

  assert {
    condition = try(
      one([
        for statement in jsondecode(aws_api_gateway_rest_api.this.policy).Statement : statement
        if statement.Sid == "DenyUnapprovedSourceIp"
        ]).Effect == "Deny" && tolist(one([
          for statement in jsondecode(aws_api_gateway_rest_api.this.policy).Statement : statement
          if statement.Sid == "DenyUnapprovedSourceIp"
      ]).Condition.NotIpAddress["aws:SourceIp"]) == var.allowed_cidrs,
      false,
    )
    error_message = "The resource policy must explicitly deny sources outside the configured CIDRs."
  }

  assert {
    condition     = aws_api_gateway_deployment.this.triggers["api_policy"] == local.api_resource_policy
    error_message = "A resource-policy change must redeploy the API stage."
  }


  assert {
    condition     = aws_cloudwatch_metric_alarm.api_4xx.metric_name == "4XXError" && contains(aws_cloudwatch_metric_alarm.api_4xx.alarm_actions, var.alarm_topic_arn) && contains(aws_cloudwatch_metric_alarm.api_5xx.alarm_actions, var.alarm_topic_arn) && contains(aws_cloudwatch_metric_alarm.unhealthy_targets.alarm_actions, var.alarm_topic_arn)
    error_message = "Edge alarms must use published API Gateway metrics and notify SNS."
  }

  assert {
    condition = (
      length(jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets) == 19 &&
      length([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if widget.type == "text"]) == 4 &&
      length([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if widget.type == "metric"]) == 13 &&
      length([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if widget.type == "log"]) == 2
    )
    error_message = "The demo dashboard must retain four operational sections, thirteen metric charts, and two log investigations."
  }

  assert {
    condition = length(setsubtract(
      toset([
        "API Gateway requests and errors",
        "API Gateway latency percentiles",
        "Internal ALB target health",
        "Internal ALB target latency",
        "ECS desired and running tasks",
        "ECS CPU utilization",
        "ECS memory utilization",
        "RDS CPU and connections",
        "RDS memory and storage headroom",
        "Valkey saturation and connections",
        "Valkey cache activity",
        "SQS backlog and DLQs",
        "SQS oldest message age",
        "Recent API Gateway 4xx/5xx",
        "Recent application errors",
      ]),
      toset([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : try(widget.properties.title, "")]),
    )) == 0
    error_message = "The dashboard must cover edge, runtime, data, queues, and recent failures."
  }

  assert {
    condition = (
      one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Internal ALB target health"]).properties.metrics[0][3] == local.query_target_group_arn_suffix &&
      one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "ECS desired and running tasks"]).properties.metrics[0][2] == "ServiceName" &&
      one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "SQS backlog and DLQs"]).properties.metrics[0][2] == "QueueName"
    )
    error_message = "ALB, ECS, and SQS charts must use the dimensions published by their AWS namespaces."
  }

  assert {
    condition = (
      strcontains(one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Recent API Gateway 4xx/5xx"]).properties.query, "/apigateway/${var.name}/access") &&
      strcontains(one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Recent application errors"]).properties.query, "/ecs/${var.name}/query-api") &&
      strcontains(one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Recent application errors"]).properties.query, "/ecs/${var.name}/command-api") &&
      strcontains(one([for widget in jsondecode(aws_cloudwatch_dashboard.demo.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Recent application errors"]).properties.query, "/ecs/${var.name}/worker")
    )
    error_message = "Log widgets must query the existing API Gateway and all three ECS log groups."
  }

  assert {
    condition = (
      aws_cloudwatch_dashboard.business.dashboard_name == "flash-booking-demo-negocio" &&
      length(jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets) == 14 &&
      length([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : widget if widget.type == "text"]) == 3 &&
      length([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : widget if widget.type == "metric"]) == 11
    )
    error_message = "The business dashboard must be a separate 14-widget view with three explanatory sections and eleven business charts."
  }

  assert {
    condition = length(setsubtract(
      toset([
        "Eventos publicados",
        "Consultas de evento concluídas",
        "Respostas de reserva aceitas",
        "Cancelamentos concluídos",
        "Interações por etapa",
        "Resultados das tentativas de reserva",
        "Taxa de aceite de reservas",
        "Interações não concluídas por etapa",
        "Acompanhamento após a reserva",
        "Tempo para consultar um evento",
        "Tempo para tentar uma reserva",
      ]),
      toset([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : try(widget.properties.title, "")]),
    )) == 0
    error_message = "The business dashboard must expose the complete customer journey with pt-BR titles."
  }

  assert {
    condition = length(setsubtract(
      toset(flatten([
        for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : try([
          for metric in widget.properties.metrics : try(metric[0].label, metric[length(metric) - 1].label, "")
        ], [])
      ])),
      toset([
        "Total de respostas",
        "Não concluídas por regra ou entrada",
        "Falhas técnicas",
        "Eventos publicados",
        "Consultas concluídas",
        "Tentativas de reserva",
        "Respostas aceitas",
        "Solicitações de cancelamento",
        "Cancelamentos concluídos",
        "Consultas de evento",
        "Consultas da reserva",
        "Taxa de aceite (%)",
        "Consulta: regra ou entrada",
        "Consulta: falha técnica",
        "Reserva: regra ou entrada",
        "Reserva: falha técnica",
        "Cancelamento: regra ou entrada",
        "Cancelamento: falha técnica",
        "Consulta não concluída por regra ou entrada",
        "Consulta com falha técnica",
        "Cancelamento não concluído por regra ou entrada",
        "Cancelamento com falha técnica",
        "Consultas da reserva concluídas",
        "Mediana (p50)",
        "95% das respostas (p95)",
      ]),
    )) == 0
    error_message = "Every visible and supporting business metric label must belong to the approved pt-BR catalog."
  }

  assert {
    condition = (
      alltrue([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : try(widget.properties.period == 300, true)]) &&
      alltrue(flatten([
        for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : try([
          for metric in widget.properties.metrics : try(metric[0].expression != "", contains(["", ".", "AWS/ApiGateway"], metric[0]))
        ], [])
      ]))
    )
    error_message = "Business charts must use five-minute periods and only existing detailed API Gateway metrics."
  }

  assert {
    condition = (
      strcontains(jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets[0].properties.markdown, "não clientes únicos, reservas únicas, vendas ou receita") &&
      one([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Taxa de aceite de reservas"]).properties.metrics[3][0].expression == "IF(taxa_tentativas>0,100*(taxa_tentativas-taxa_4xx-taxa_5xx)/taxa_tentativas,0)" &&
      one([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Interações por etapa"]).properties.metrics[0][4] == "Resource" &&
      one([for widget in jsondecode(aws_cloudwatch_dashboard.business.dashboard_body).widgets : widget if try(widget.properties.title, "") == "Interações por etapa"]).properties.metrics[0][8] == "Method"
    )
    error_message = "The business view must disclose its counting boundary, calculate acceptance exactly, and use route-level metrics."
  }
}
