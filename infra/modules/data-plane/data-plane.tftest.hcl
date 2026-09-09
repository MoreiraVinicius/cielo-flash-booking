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
  }

  assert {
    condition     = !aws_db_instance.postgres.publicly_accessible && aws_db_instance.postgres.storage_encrypted && aws_db_instance.postgres.manage_master_user_password
    error_message = "PostgreSQL must be private, encrypted, and use an RDS-managed password secret."
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
}
