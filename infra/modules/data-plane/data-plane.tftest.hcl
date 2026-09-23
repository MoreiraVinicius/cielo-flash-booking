mock_provider "aws" {}

run "keeps_data_private_encrypted_and_message_paths_isolated" {
  command = apply

  variables {
    name                     = "flash-booking-demo"
    vpc_id                   = "vpc-123"
    isolated_data_subnet_ids = ["subnet-data-a", "subnet-data-b"]
    rds_security_group_id    = "sg-rds"
    valkey_security_group_id = "sg-valkey"
    ses_sender_email         = "demo@example.com"
    alarm_topic_arn          = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = !aws_db_instance.postgres.publicly_accessible && toset(aws_db_subnet_group.postgres.subnet_ids) == toset(["subnet-data-a", "subnet-data-b"]) && aws_db_instance.postgres.storage_encrypted && aws_db_instance.postgres.manage_master_user_password
    error_message = "PostgreSQL must be private, encrypted, and use an RDS-managed password secret when administrative access is disabled."
  }

  assert {
    condition     = aws_elasticache_replication_group.valkey.at_rest_encryption_enabled && aws_elasticache_replication_group.valkey.transit_encryption_enabled && contains(aws_elasticache_replication_group.valkey.security_group_ids, var.valkey_security_group_id)
    error_message = "Valkey must be encrypted and accept access only through its supplied security group."
  }

  assert {
    condition     = jsondecode(aws_sqs_queue.expiration.redrive_policy).deadLetterTargetArn == aws_sqs_queue.expiration_dlq.arn && jsondecode(aws_sqs_queue.notification.redrive_policy).deadLetterTargetArn == aws_sqs_queue.notification_dlq.arn
    error_message = "Expiration and notification queues must use distinct dead-letter queues."
  }

  assert {
    condition     = aws_sqs_queue.expiration.sqs_managed_sse_enabled && aws_sqs_queue.notification.sqs_managed_sse_enabled
    error_message = "Worker queues must use SQS-managed server-side encryption."
  }


  assert {
    condition     = contains(aws_cloudwatch_metric_alarm.database_cpu.alarm_actions, var.alarm_topic_arn) && alltrue([for alarm in aws_cloudwatch_metric_alarm.queue_age : contains(alarm.alarm_actions, var.alarm_topic_arn)]) && alltrue([for alarm in aws_cloudwatch_metric_alarm.dead_letter_messages : contains(alarm.alarm_actions, var.alarm_topic_arn)])
    error_message = "Database, queue-age, and DLQ alarms must notify the operational SNS topic."
  }
}

run "exposes_rds_only_for_explicit_administrative_access" {
  command = apply

  variables {
    name                     = "flash-booking-demo"
    vpc_id                   = "vpc-123"
    isolated_data_subnet_ids = ["subnet-data-a", "subnet-data-b"]
    public_access_enabled    = true
    rds_security_group_id    = "sg-rds"
    valkey_security_group_id = "sg-valkey"
    ses_sender_email         = "demo@example.com"
    alarm_topic_arn          = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = aws_db_instance.postgres.publicly_accessible && aws_db_instance.postgres.db_subnet_group_name == aws_db_subnet_group.postgres.name && toset(aws_db_subnet_group.postgres.subnet_ids) == toset(["subnet-data-a", "subnet-data-b"])
    error_message = "Public administrative access must retain the existing RDS subnet group."
  }
}

run "requires_tls_and_preserves_rds_managed_security" {
  command = apply

  variables {
    name                     = "flash-booking-demo"
    vpc_id                   = "vpc-123"
    isolated_data_subnet_ids = ["subnet-data-a", "subnet-data-b"]
    rds_security_group_id    = "sg-rds"
    valkey_security_group_id = "sg-valkey"
    ses_sender_email         = "demo@example.com"
    alarm_topic_arn          = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = aws_db_instance.postgres.storage_encrypted && aws_db_instance.postgres.manage_master_user_password && aws_db_instance.postgres.parameter_group_name == aws_db_parameter_group.postgres.name && one([for parameter in aws_db_parameter_group.postgres.parameter : parameter if parameter.name == "rds.force_ssl"]).value == "1"
    error_message = "PostgreSQL must preserve encryption and managed credentials while requiring TLS."
  }
}
