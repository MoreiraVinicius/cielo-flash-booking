mock_provider "aws" {}

run "creates_two_private_application_and_isolated_data_subnets" {
  command = apply

  variables {
    name               = "flash-booking-demo"
    availability_zones = ["sa-east-1a", "sa-east-1c"]
  }

  assert {
    condition     = length(aws_subnet.public) == 2 && length(aws_subnet.private_app) == 2 && length(aws_subnet.isolated_data) == 2
    error_message = "The network must span two public, two private application, and two isolated data subnets."
  }

  assert {
    condition     = one([for route in aws_route_table.private_app.route : route if route.cidr_block == "0.0.0.0/0"]).nat_gateway_id == aws_nat_gateway.this.id
    error_message = "Application subnets must use the NAT gateway for outbound traffic."
  }

  assert {
    condition     = length(aws_route_table.isolated_data.route) == 0
    error_message = "Data subnets must not have an internet or NAT route."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.alb_from_vpc_link.referenced_security_group_id == aws_security_group.vpc_link.id && aws_vpc_security_group_ingress_rule.ecs_from_alb.referenced_security_group_id == aws_security_group.alb.id
    error_message = "Only VPC Link reaches the ALB and only the ALB reaches ECS tasks."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.rds_from_ecs.referenced_security_group_id == aws_security_group.ecs_tasks.id && aws_vpc_security_group_ingress_rule.valkey_from_ecs.referenced_security_group_id == aws_security_group.ecs_tasks.id
    error_message = "Only ECS tasks may reach PostgreSQL and Valkey."
  }
}
