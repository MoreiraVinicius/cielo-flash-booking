output "database_host" {
  description = "Private PostgreSQL endpoint hostname."
  value       = aws_db_instance.postgres.address
}

output "database_port" {
  description = "PostgreSQL endpoint port."
  value       = aws_db_instance.postgres.port
}

output "database_secret_arn" {
  description = "ARN of the RDS-managed Secrets Manager secret containing database credentials."
  value       = try(aws_db_instance.postgres.master_user_secret[0].secret_arn, null)
}

output "valkey_primary_endpoint" {
  description = "Private TLS endpoint used by ECS tasks."
  value       = aws_elasticache_replication_group.valkey.primary_endpoint_address
}

output "valkey_port" {
  description = "Valkey endpoint port."
  value       = aws_elasticache_replication_group.valkey.port
}

output "expiration_queue_arn" {
  description = "ARN of the private expiration queue."
  value       = aws_sqs_queue.expiration.arn
}

output "expiration_queue_url" {
  description = "URL of the private expiration queue."
  value       = aws_sqs_queue.expiration.url
}

output "notification_queue_arn" {
  description = "ARN of the private notification queue."
  value       = aws_sqs_queue.notification.arn
}

output "notification_queue_url" {
  description = "URL of the private notification queue."
  value       = aws_sqs_queue.notification.url
}

output "ses_sender_email" {
  description = "SES sender identity that the worker may use."
  value       = aws_sesv2_email_identity.sender.email_identity
}
