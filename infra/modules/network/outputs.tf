output "vpc_id" {
  description = "VPC identifier used by the remaining demo modules."
  value       = aws_vpc.this.id
}

output "private_app_subnet_ids" {
  description = "Private subnets for ECS services and VPC Link ENIs."
  value       = [for index in ["0", "1"] : aws_subnet.private_app[index].id]
}

output "isolated_data_subnet_ids" {
  description = "Isolated subnets for RDS and ElastiCache subnet groups."
  value       = [for index in ["0", "1"] : aws_subnet.isolated_data[index].id]
}

output "vpc_link_security_group_id" {
  description = "Security group attached to the API Gateway VPC Link."
  value       = aws_security_group.vpc_link.id
}

output "alb_security_group_id" {
  description = "Security group attached to the internal ALB."
  value       = aws_security_group.alb.id
}

output "ecs_tasks_security_group_id" {
  description = "Security group attached to ECS task ENIs."
  value       = aws_security_group.ecs_tasks.id
}

output "rds_security_group_id" {
  description = "Security group attached to RDS."
  value       = aws_security_group.rds.id
}

output "valkey_security_group_id" {
  description = "Security group attached to ElastiCache for Valkey."
  value       = aws_security_group.valkey.id
}
