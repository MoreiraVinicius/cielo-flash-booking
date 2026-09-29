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
variable "notification_consumer_enabled" {
  description = "Whether the worker consumes reservation-notification messages and invokes the configured email provider."
  type        = bool
  default     = true
}
variable "discord_webhook_secret_arn" {
  description = "Optional ARN of the Discord webhook SecretString; the webhook URL is never passed to Terraform."
  type        = string
  default     = ""

  validation {
    condition     = var.discord_webhook_secret_arn == "" || startswith(var.discord_webhook_secret_arn, "arn:")
    error_message = "discord_webhook_secret_arn must be empty or a Secrets Manager ARN."
  }
}
variable "executive_summary_operational_alarms" {
  description = "CloudWatch alarms and public-friendly labels queried by the executive summary worker."
  type = list(object({
    name  = string
    label = string
  }))
  default = []

  validation {
    condition     = length(var.executive_summary_operational_alarms) <= 12
    error_message = "At most 12 operational alarms may be configured for event summaries."
  }
}
variable "ses_sender_email" { type = string }
variable "alarm_topic_arn" {
  type    = string
  default = null
}
