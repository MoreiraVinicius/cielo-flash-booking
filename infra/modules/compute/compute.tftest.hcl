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
    name                        = "flash-booking-demo"
    aws_region                  = "sa-east-1"
    vpc_id                      = "vpc-123"
    private_app_subnet_ids      = ["subnet-app-a", "subnet-app-b"]
    ecs_tasks_security_group_id = "sg-ecs"
    database_host               = "database.internal"
    database_port               = 5432
    database_secret_arn         = "arn:aws:secretsmanager:sa-east-1:123456789012:secret:database"
    valkey_primary_endpoint     = "valkey.internal"
    valkey_port                 = 6379
    expiration_queue_arn        = "arn:aws:sqs:sa-east-1:123456789012:expiration"
    expiration_queue_url        = "https://sqs.sa-east-1.amazonaws.com/123456789012/expiration"
    notification_queue_arn      = "arn:aws:sqs:sa-east-1:123456789012:notification"
    notification_queue_url      = "https://sqs.sa-east-1.amazonaws.com/123456789012/notification"
    ses_sender_email            = "demo@example.com"
    alarm_topic_arn             = "arn:aws:sns:sa-east-1:123456789012:alerts"
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
    condition     = alltrue([for definition in [aws_ecs_task_definition.query_api, aws_ecs_task_definition.command_api] : one([for item in jsondecode(definition.container_definitions)[0].environment : item.value if item.name == "MANAGEMENT_HEALTH_REDIS_ENABLED"]) == "false"])
    error_message = "ALB health checks must not fail when the optional Valkey cache falls back to PostgreSQL."
  }

  assert {
    condition     = !contains(jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement[0].Action, "secretsmanager:GetSecretValue") && contains(jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement[1].Action, "sqs:ReceiveMessage") && !contains(jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement[1].Action, "sqs:ChangeMessageVisibility") && !contains(jsondecode(aws_iam_role_policy.worker_messaging.policy).Statement[2].Action, "ses:SendRawEmail")
    error_message = "Only the execution role reads the database secret, while the worker receives queue messages."
  }
}
