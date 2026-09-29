variable "name" {
  type    = string
  default = "flash-booking-demo"
}

variable "aws_region" {
  type    = string
  default = "sa-east-1"
}

variable "aws_profile" {
  description = "Named AWS CLI profile containing temporary operator credentials."
  type        = string
  default     = null
  nullable    = true
}

variable "availability_zones" {
  type        = list(string)
  description = "Two zones from aws_region used for every subnet tier."
}

variable "image_tag" {
  type        = string
  description = "Immutable application image tag that must exist in ECR before ECS deployment."
}

variable "ses_sender_email" {
  type        = string
  description = "Verified SES sender identity for reservation notifications."
}

variable "notification_consumer_enabled" {
  description = "Temporarily enable or pause the worker notification consumer without stopping outbox publication or reservation expiration."
  type        = bool
  default     = true
}

variable "reservation_owner_role_arn" {
  description = "Optional IAM role ARN of the single external module authorized to exchange reservation messages."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.reservation_owner_role_arn == null || can(regex("^arn:[^:]+:iam::[0-9]{12}:role/.+$", var.reservation_owner_role_arn))
    error_message = "reservation_owner_role_arn must be null or an IAM role ARN."
  }
}

variable "discord_webhook_secret_arn" {
  description = "Optional ARN of the AWS Secrets Manager secret containing the Discord Incoming Webhook URL as SecretString."
  type        = string
  default     = ""

  validation {
    condition     = var.discord_webhook_secret_arn == "" || startswith(var.discord_webhook_secret_arn, "arn:")
    error_message = "discord_webhook_secret_arn must be empty or a Secrets Manager ARN."
  }
}

variable "trusted_principal_arns" {
  type        = list(string)
  description = "Principals that may assume ApiInvokerRole."
}

variable "allowed_cidrs" {
  type        = list(string)
  description = "Approved public source CIDRs for the API resource policy."
}

variable "budget_alert_email" {
  type        = string
  description = "Recipient of the 50%, 80%, and 100% notifications for the US$5 demo budget."
}

variable "database_administrative_access_enabled" {
  description = "Temporarily make the PostgreSQL RDS endpoint public for one administrative IPv4 /32."
  type        = bool
  default     = false
}

variable "database_administrative_cidr" {
  description = "Single IPv4 /32 permitted to administer the public PostgreSQL RDS endpoint."
  type        = string
  default     = null
  nullable    = true

  validation {
    condition     = var.database_administrative_cidr == null || (can(cidrhost(var.database_administrative_cidr, 0)) && can(regex("^([0-9]{1,3}\\.){3}[0-9]{1,3}/32$", var.database_administrative_cidr)))
    error_message = "database_administrative_cidr must be a single IPv4 /32."
  }

  validation {
    condition     = !var.database_administrative_access_enabled || var.database_administrative_cidr != null
    error_message = "database_administrative_cidr is required when database_administrative_access_enabled is true."
  }
}
