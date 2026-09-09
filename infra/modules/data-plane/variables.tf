variable "name" {
  description = "Prefix used to identify the data-plane resources."
  type        = string
}

variable "vpc_id" {
  description = "VPC that hosts the private data-plane resources."
  type        = string
}

variable "isolated_data_subnet_ids" {
  description = "Exactly two isolated subnets for RDS and ElastiCache subnet groups."
  type        = list(string)

  validation {
    condition     = length(var.isolated_data_subnet_ids) == 2
    error_message = "isolated_data_subnet_ids must contain exactly two subnets."
  }
}

variable "rds_security_group_id" {
  description = "Security group that accepts PostgreSQL only from ECS tasks."
  type        = string
}

variable "valkey_security_group_id" {
  description = "Security group that accepts Valkey only from ECS tasks."
  type        = string
}

variable "database_name" {
  description = "Initial PostgreSQL database name."
  type        = string
  default     = "flashbooking"
}

variable "database_master_username" {
  description = "RDS master username. Its password is generated and managed by RDS in Secrets Manager."
  type        = string
  default     = "flashbooking"
}

variable "ses_sender_email" {
  description = "Verified SES sender identity used by the worker in the demo account."
  type        = string

  validation {
    condition     = can(regex("^[^@[:space:]]+@[^@[:space:]]+\\.[^@[:space:]]+$", var.ses_sender_email))
    error_message = "ses_sender_email must be a valid email address."
  }
}

variable "queue_visibility_timeout_seconds" {
  description = "Visibility timeout used by both worker queues."
  type        = number
  default     = 60

  validation {
    condition     = var.queue_visibility_timeout_seconds >= 30 && var.queue_visibility_timeout_seconds <= 43200
    error_message = "queue_visibility_timeout_seconds must be between 30 seconds and 12 hours."
  }
}
