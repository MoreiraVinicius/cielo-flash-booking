locals {
  tags = {
    ManagedBy = "Terraform"
    Project   = var.name
  }
}

resource "aws_db_subnet_group" "postgres" {
  name       = "${var.name}-postgres"
  subnet_ids = var.isolated_data_subnet_ids

  tags = merge(local.tags, { Name = "${var.name}-postgres" })
}

resource "aws_db_instance" "postgres" {
  identifier                  = "${var.name}-postgres"
  allocated_storage           = 20
  max_allocated_storage       = 30
  storage_type                = "gp3"
  storage_encrypted           = true
  engine                      = "postgres"
  engine_version              = "16"
  instance_class              = "db.t4g.micro"
  db_name                     = var.database_name
  username                    = var.database_master_username
  manage_master_user_password = true
  publicly_accessible         = false
  multi_az                    = false
  backup_retention_period     = 1
  backup_window               = "03:00-03:30"
  maintenance_window          = "sun:04:00-sun:04:30"
  deletion_protection         = false
  skip_final_snapshot         = true
  apply_immediately           = true
  db_subnet_group_name        = aws_db_subnet_group.postgres.name
  vpc_security_group_ids      = [var.rds_security_group_id]
  auto_minor_version_upgrade  = true
  copy_tags_to_snapshot       = true

  tags = merge(local.tags, { Name = "${var.name}-postgres" })
}

resource "aws_elasticache_subnet_group" "valkey" {
  name       = "${var.name}-valkey"
  subnet_ids = var.isolated_data_subnet_ids
}

resource "aws_elasticache_replication_group" "valkey" {
  replication_group_id       = "${var.name}-valkey"
  description                = "Private Valkey cache for ${var.name}"
  engine                     = "valkey"
  engine_version             = "7.2"
  node_type                  = "cache.t4g.micro"
  port                       = 6379
  num_cache_clusters         = 1
  automatic_failover_enabled = false
  multi_az_enabled           = false
  at_rest_encryption_enabled = true
  transit_encryption_enabled = true
  subnet_group_name          = aws_elasticache_subnet_group.valkey.name
  security_group_ids         = [var.valkey_security_group_id]
  apply_immediately          = true

  tags = local.tags
}

resource "aws_sqs_queue" "expiration_dlq" {
  name                      = "${var.name}-expiration-dlq"
  sqs_managed_sse_enabled   = true
  message_retention_seconds = 1209600

  tags = merge(local.tags, { Name = "${var.name}-expiration-dlq" })
}

resource "aws_sqs_queue" "notification_dlq" {
  name                      = "${var.name}-notification-dlq"
  sqs_managed_sse_enabled   = true
  message_retention_seconds = 1209600

  tags = merge(local.tags, { Name = "${var.name}-notification-dlq" })
}

resource "aws_sqs_queue" "expiration" {
  name                       = "${var.name}-expiration"
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.expiration_dlq.arn
    maxReceiveCount     = 5
  })

  tags = merge(local.tags, { Name = "${var.name}-expiration" })
}

resource "aws_sqs_queue" "notification" {
  name                       = "${var.name}-notification"
  sqs_managed_sse_enabled    = true
  visibility_timeout_seconds = var.queue_visibility_timeout_seconds
  redrive_policy = jsonencode({
    deadLetterTargetArn = aws_sqs_queue.notification_dlq.arn
    maxReceiveCount     = 5
  })

  tags = merge(local.tags, { Name = "${var.name}-notification" })
}

resource "aws_sesv2_email_identity" "sender" {
  email_identity = var.ses_sender_email

  tags = merge(local.tags, { Name = "${var.name}-sender" })
}

resource "aws_cloudwatch_metric_alarm" "database_cpu" {
  alarm_name          = "${var.name}-database-cpu"
  alarm_description   = "PostgreSQL CPU remained above 80 percent."
  namespace           = "AWS/RDS"
  metric_name         = "CPUUtilization"
  statistic           = "Average"
  period              = 300
  evaluation_periods  = 2
  threshold           = 80
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "missing"
  alarm_actions       = compact([var.alarm_topic_arn])

  dimensions = { DBInstanceIdentifier = aws_db_instance.postgres.identifier }
}

resource "aws_cloudwatch_metric_alarm" "queue_age" {
  for_each = {
    expiration   = aws_sqs_queue.expiration.name
    notification = aws_sqs_queue.notification.name
  }

  alarm_name          = "${var.name}-${each.key}-queue-age"
  alarm_description   = "A worker queue has messages older than the processing target."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateAgeOfOldestMessage"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 2
  threshold           = 30
  comparison_operator = "GreaterThanThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = compact([var.alarm_topic_arn])

  dimensions = { QueueName = each.value }
}

resource "aws_cloudwatch_metric_alarm" "dead_letter_messages" {
  for_each = {
    expiration   = aws_sqs_queue.expiration_dlq.name
    notification = aws_sqs_queue.notification_dlq.name
  }

  alarm_name          = "${var.name}-${each.key}-dlq-messages"
  alarm_description   = "A dead-letter queue contains failed messages."
  namespace           = "AWS/SQS"
  metric_name         = "ApproximateNumberOfMessagesVisible"
  statistic           = "Maximum"
  period              = 60
  evaluation_periods  = 1
  threshold           = 1
  comparison_operator = "GreaterThanOrEqualToThreshold"
  treat_missing_data  = "notBreaching"
  alarm_actions       = compact([var.alarm_topic_arn])

  dimensions = { QueueName = each.value }
}
