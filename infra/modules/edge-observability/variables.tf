variable "name" { type = string }
variable "aws_region" { type = string }
variable "vpc_id" { type = string }
variable "private_app_subnet_ids" {
  type = list(string)
  validation {
    condition     = length(var.private_app_subnet_ids) == 2
    error_message = "private_app_subnet_ids must contain exactly two subnets."
  }
}
variable "vpc_link_security_group_id" { type = string }
variable "alb_security_group_id" { type = string }
variable "query_target_group_arn" { type = string }
variable "command_target_group_arn" { type = string }
variable "trusted_principal_arns" {
  description = "IAM principals that may assume the demo API invoker role."
  type        = list(string)
  validation {
    condition     = length(var.trusted_principal_arns) > 0
    error_message = "trusted_principal_arns must not be empty."
  }
}
variable "allowed_cidrs" {
  description = "Public source CIDRs allowed by the API resource policy."
  type        = list(string)
  validation {
    condition     = length(var.allowed_cidrs) > 0
    error_message = "allowed_cidrs must not be empty."
  }
}
variable "budget_alert_email" {
  description = "Email address that receives the 50%, 80%, and 100% alerts for the US$50 demo budget."
  type        = string
}

variable "budget_emergency_topic_arn" {
  description = "Dedicated SNS topic that receives the actual-spend 100% budget notification and triggers the emergency stop."
  type        = string
}

variable "ecs_cluster_arn" {
  description = "ECS cluster that owns the recoverable demo services."
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name used in Application Auto Scaling resource identifiers."
  type        = string
}

variable "ecs_service_names" {
  description = "The three ECS services set to zero by the emergency stop."
  type        = list(string)
}

variable "ecs_service_arns" {
  description = "Exact ECS service ARNs granted to the emergency-stop Lambda."
  type        = list(string)
}

variable "ecs_scalable_target_arns" {
  description = "Exact Application Auto Scaling target ARNs frozen by the emergency-stop Lambda."
  type        = list(string)
}

variable "database_identifier" {
  description = "RDS instance identifier stopped by the emergency-stop Lambda."
  type        = string
}

variable "database_arn" {
  description = "Exact RDS instance ARN granted to the emergency-stop Lambda."
  type        = string
}
variable "waf_rate_limit" {
  description = "Maximum requests from one source IP in the WAF evaluation window."
  type        = number
  default     = 100
  validation {
    condition     = var.waf_rate_limit >= 100 && var.waf_rate_limit <= 2000000000
    error_message = "waf_rate_limit must be between 100 and 2000000000."
  }
}

variable "alarm_topic_arn" {
  description = "SNS topic that receives operational alarms."
  type        = string
  default     = null
}
