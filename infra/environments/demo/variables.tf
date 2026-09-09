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
