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
  }

  assert {
    condition     = aws_lb.internal.internal && aws_api_gateway_method.create_event.authorization == "AWS_IAM" && aws_api_gateway_method.get_event.authorization == "AWS_IAM" && aws_api_gateway_method.create_reservation.authorization == "AWS_IAM" && aws_api_gateway_method.get_reservation.authorization == "AWS_IAM" && aws_api_gateway_method.cancel_reservation.authorization == "AWS_IAM"
    error_message = "The public API must require IAM while the ALB remains internal."
  }

  assert {
    condition     = aws_api_gateway_integration.get_event.connection_id == aws_apigatewayv2_vpc_link.private.id && aws_api_gateway_integration.create_reservation.connection_id == aws_apigatewayv2_vpc_link.private.id && contains(aws_apigatewayv2_vpc_link.private.security_group_ids, var.vpc_link_security_group_id)
    error_message = "API Gateway must use VPC Link V2 to reach only the internal ALB."
  }

  assert {
    condition     = aws_lb_listener_rule.get_event.action[0].target_group_arn == var.query_target_group_arn && aws_lb_listener_rule.create_event.action[0].target_group_arn == var.command_target_group_arn && aws_lb_listener_rule.cancel_reservation.action[0].target_group_arn == var.command_target_group_arn
    error_message = "GET routes must reach query-api, while POST and DELETE routes reach command-api."
  }

  assert {
    condition     = one([for rule in aws_wafv2_web_acl.api.rule : rule if rule.name == "RateLimit"]).statement[0].rate_based_statement[0].limit == var.waf_rate_limit && length(aws_budgets_budget.demo.notification) == 3
    error_message = "The edge must rate-limit requests and create all three budget notifications."
  }

  assert {
    condition     = jsondecode(aws_iam_role_policy.api_invoker.policy).Statement[0].Action == "execute-api:Invoke"
    error_message = "ApiInvokerRole must grant only API invocation and never infrastructure provisioning."
  }
}
