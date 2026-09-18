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
variable "ecs_tasks_security_group_id" { type = string }
variable "image_tag" {
  description = "Immutable image tag published to the repository before deployment."
  type        = string
  default     = "demo"
}
variable "database_name" {
  type    = string
  default = "flashbooking"
}
variable "database_host" { type = string }
variable "database_port" { type = number }
variable "database_secret_arn" {
  description = "RDS-managed secret ARN. No secret value is accepted by this module."
  type        = string
}
variable "valkey_primary_endpoint" { type = string }
variable "valkey_port" { type = number }
variable "expiration_queue_arn" { type = string }
variable "expiration_queue_url" { type = string }
variable "notification_queue_arn" { type = string }
variable "notification_queue_url" { type = string }
variable "ses_sender_email" { type = string }
variable "alarm_topic_arn" {
  type    = string
  default = null
}
