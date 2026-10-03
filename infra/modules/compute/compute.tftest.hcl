mock_provider "aws" {
  mock_resource "aws_iam_role" {
    defaults = {
      arn = "arn:aws:iam::123456789012:role/mock"
    }
  }

  mock_resource "aws_lb_target_group" {
    defaults = {
      arn = "arn:aws:elasticloadbalancing:sa-east-1:123456789012:targetgroup/mock/1234567890abcdef"
    }
  }
}

run "uses_one_image_with_separate_least_privilege_services" {
  command = apply

  variables {
    name                             = "flash-booking-demo"
    aws_region                       = "sa-east-1"
    vpc_id                           = "vpc-123"
    private_app_subnet_ids           = ["subnet-app-a", "subnet-app-b"]
    ecs_tasks_security_group_id      = "sg-ecs"
    database_host                    = "database.internal"
    database_port                    = 5432
    database_secret_arn              = "arn:aws:secretsmanager:sa-east-1:123456789012:secret:database"
    valkey_primary_endpoint          = "valkey.internal"
    valkey_port                      = 6379
    expiration_queue_arn             = "arn:aws:sqs:sa-east-1:123456789012:expiration"
    expiration_queue_url             = "https://sqs.sa-east-1.amazonaws.com/123456789012/expiration"
    notification_queue_arn           = "arn:aws:sqs:sa-east-1:123456789012:notification"
    notification_queue_url           = "https://sqs.sa-east-1.amazonaws.com/123456789012/notification"
    reservation_to_owner_queue_arn   = "arn:aws:sqs:sa-east-1:123456789012:reservation-to-owner"
    reservation_to_owner_queue_url   = "https://sqs.sa-east-1.amazonaws.com/123456789012/reservation-to-owner"
    reservation_from_owner_queue_arn = "arn:aws:sqs:sa-east-1:123456789012:reservation-from-owner"
    reservation_from_owner_queue_url = "https://sqs.sa-east-1.amazonaws.com/123456789012/reservation-from-owner"
    discord_webhook_secret_arn       = "arn:aws:secretsmanager:sa-east-1:123456789012:secret:discord-webhook"
    executive_summary_operational_alarms = [{
      name  = "flash-booking-demo-worker-running-tasks"
      label = "Worker sem tarefas ativas"
    }]
    ses_sender_email = "demo@example.com"
    alarm_topic_arn  = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = aws_ecr_repository.application.image_tag_mutability == "IMMUTABLE" && aws_ecr_repository.application.image_scanning_configuration[0].scan_on_push && aws_ecr_repository.application.force_delete
    error_message = "The application repository must scan immutable image tags and support complete demo teardown."
  }

  assert {
    condition     = alltrue([for definition in [aws_ecs_task_definition.query_api, aws_ecs_task_definition.command_api, aws_ecs_task_definition.worker] : length(jsondecode(definition.container_definitions)[0].healthCheck.command) > 0])
    error_message = "Every ECS task definition must carry a container health check."
  }

  assert {
    condition     = aws_appautoscaling_target.query_api.max_capacity == 4 && aws_appautoscaling_target.command_api.max_capacity == 4
    error_message = "Both API services must be able to scale beyond one task."
  }

  assert {
    condition     = length(aws_cloudwatch_metric_alarm.worker_running_tasks.alarm_actions) == 1 && contains(aws_cloudwatch_metric_alarm.worker_running_tasks.alarm_actions, var.alarm_topic_arn)
    error_message = "Worker liveness alarms must notify the operational SNS topic."
  }

  assert {
    condition     = aws_ecs_task_definition.query_api.network_mode == "awsvpc" && aws_ecs_task_definition.command_api.network_mode == "awsvpc" && aws_ecs_task_definition.worker.network_mode == "awsvpc"
    error_message = "Every service must use Fargate awsvpc networking."
  }

  assert {
    condition     = aws_ecs_service.query_api.health_check_grace_period_seconds == 180 && aws_ecs_service.command_api.health_check_grace_period_seconds == 180
    error_message = "HTTP services must allow Spring Boot to start before ALB health checks can stop their tasks."
  }

  assert {
    condition     = jsondecode(aws_ecs_task_definition.query_api.container_definitions)[0].image == jsondecode(aws_ecs_task_definition.command_api.container_definitions)[0].image && jsondecode(aws_ecs_task_definition.command_api.container_definitions)[0].image == jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].image
    error_message = "Query API, command API, and worker must run the same image."
  }

  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.query_api.container_definitions)[0].environment : item.value if item.name == "SPRING_PROFILES_ACTIVE"]) == "query-api" && one([for item in jsondecode(aws_ecs_task_definition.command_api.container_definitions)[0].environment : item.value if item.name == "SPRING_PROFILES_ACTIVE"]) == "command-api" && one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "SPRING_PROFILES_ACTIVE"]) == "worker"
    error_message = "Each ECS task definition must select its dedicated Spring profile."
  }

  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "NOTIFICATION_CONSUMER_ENABLED"]) == "true"
    error_message = "The notification consumer must be enabled by default."
  }

  assert {
    condition     = alltrue([for definition in [aws_ecs_task_definition.query_api, aws_ecs_task_definition.command_api] : one([for item in jsondecode(definition.container_definitions)[0].environment : item.value if item.name == "MANAGEMENT_HEALTH_REDIS_ENABLED"]) == "false"])
    error_message = "ALB health checks must not fail when the optional Valkey cache falls back to PostgreSQL."
  }

  assert {
    condition     = !contains(jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement[0].Action, "secretsmanager:GetSecretValue") && contains(one([for statement in jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement : statement.Action if statement.Sid == "ConsumeWorkerQueues"]), "sqs:ReceiveMessage") && !contains(one([for statement in jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement : statement.Action if statement.Sid == "ConsumeWorkerQueues"]), "sqs:ChangeMessageVisibility") && !contains(one([for statement in jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement : statement.Action if statement.Sid == "SendReservationEmail"]), "ses:SendRawEmail")
    error_message = "Only the execution role reads the database secret, while the worker receives queue messages."
  }

  assert {
    condition = (
      one([for statement in jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement : statement.Resource if statement.Sid == "PublishReservationMessagesToOwner"]) == [var.reservation_to_owner_queue_arn] &&
      one([for statement in jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement : statement.Resource if statement.Sid == "ConsumeReservationResolutionsFromOwner"]) == [var.reservation_from_owner_queue_arn] &&
      one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "OUTBOX_PUBLISHER_OWNER_QUEUE_URL"]) == var.reservation_to_owner_queue_url &&
      one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "CONFIRMATION_CONSUMER_ENABLED"]) == "true" &&
      one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "CONFIRMATION_CONSUMER_QUEUE_URL"]) == var.reservation_from_owner_queue_url
    )
    error_message = "The worker may publish to the owner queue and consume resolutions only from the inbound queue."
  }

  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "EXECUTIVE_SUMMARY_DISCORD_WEBHOOK_SECRET_ARN"]) == var.discord_webhook_secret_arn && one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "EXECUTIVE_SUMMARY_OPERATIONAL_SIGNALS_ALARMS_0_NAME"]) == "flash-booking-demo-worker-running-tasks" && !anytrue([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : can(regex("/api/webhooks/", item.value))])
    error_message = "The worker must receive only the configured secret ARN and allowlisted alarm identity."
  }

  assert {
    condition     = one([for statement in jsondecode(aws_iam_role_policy.worker_executive_summary.policy).Statement : statement.Resource if statement.Sid == "ReadDiscordWebhook"]) == [var.discord_webhook_secret_arn] && contains(one([for statement in jsondecode(aws_iam_role_policy.worker_executive_summary.policy).Statement : statement.Action if statement.Sid == "ReadDiscordWebhook"]), "secretsmanager:GetSecretValue")
    error_message = "The worker may read only the configured Discord webhook secret ARN."
  }
}

run "pauses_only_notification_consumer" {
  command = plan

  variables {
    name                             = "flash-booking-demo"
    aws_region                       = "sa-east-1"
    vpc_id                           = "vpc-123"
    private_app_subnet_ids           = ["subnet-app-a", "subnet-app-b"]
    ecs_tasks_security_group_id      = "sg-ecs"
    database_host                    = "database.internal"
    database_port                    = 5432
    database_secret_arn              = "arn:aws:secretsmanager:sa-east-1:123456789012:secret:database"
    valkey_primary_endpoint          = "valkey.internal"
    valkey_port                      = 6379
    expiration_queue_arn             = "arn:aws:sqs:sa-east-1:123456789012:expiration"
    expiration_queue_url             = "https://sqs.sa-east-1.amazonaws.com/123456789012/expiration"
    notification_queue_arn           = "arn:aws:sqs:sa-east-1:123456789012:notification"
    notification_queue_url           = "https://sqs.sa-east-1.amazonaws.com/123456789012/notification"
    reservation_to_owner_queue_arn   = "arn:aws:sqs:sa-east-1:123456789012:reservation-to-owner"
    reservation_to_owner_queue_url   = "https://sqs.sa-east-1.amazonaws.com/123456789012/reservation-to-owner"
    reservation_from_owner_queue_arn = "arn:aws:sqs:sa-east-1:123456789012:reservation-from-owner"
    reservation_from_owner_queue_url = "https://sqs.sa-east-1.amazonaws.com/123456789012/reservation-from-owner"
    notification_consumer_enabled    = false
    discord_webhook_secret_arn       = ""
    ses_sender_email                 = "demo@example.com"
    alarm_topic_arn                  = "arn:aws:sns:sa-east-1:123456789012:alerts"
  }

  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "NOTIFICATION_CONSUMER_ENABLED"]) == "false"
    error_message = "The worker must receive a disabled notification consumer when the flag is false."
  }

  assert {
    condition     = !anytrue([for statement in jsondecode(aws_iam_role_policy.worker_executive_summary.policy).Statement : contains(statement.Action, "secretsmanager:GetSecretValue")]) && one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "EXECUTIVE_SUMMARY_DISCORD_WEBHOOK_SECRET_ARN"]) == ""
    error_message = "Without a configured Discord ARN the worker must not receive secret-read permission or a secret value."
  }

  assert {
    condition     = one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "OUTBOX_PUBLISHER_ENABLED"]) == "true" && one([for item in jsondecode(aws_ecs_task_definition.worker.container_definitions)[0].environment : item.value if item.name == "EXPIRATION_CONSUMER_ENABLED"]) == "true"
    error_message = "Pausing notification delivery must not pause outbox publication or expiration."
  }
}
