locals {
  tags = {
    ManagedBy = "Terraform"
    Project   = var.name
  }

  image_uri = "${aws_ecr_repository.application.repository_url}:${var.image_tag}"

  common_environment = [
    { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${var.database_host}:${var.database_port}/${var.database_name}?sslmode=require" },
    { name = "SPRING_DATA_REDIS_HOST", value = var.valkey_primary_endpoint },
    { name = "SPRING_DATA_REDIS_PORT", value = tostring(var.valkey_port) },
    { name = "SPRING_DATA_REDIS_SSL_ENABLED", value = "true" },
    { name = "MANAGEMENT_HEALTH_REDIS_ENABLED", value = "false" }
  ]

  database_secrets = [
    { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.database_secret_arn}:username::" },
    { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.database_secret_arn}:password::" }
  ]

  ecs_task_assume_role_policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect    = "Allow"
      Action    = "sts:AssumeRole"
      Principal = { Service = "ecs-tasks.amazonaws.com" }
    }]
  })
}

resource "aws_ecr_repository" "application" {
  name                 = "${var.name}-application"
  image_tag_mutability = "IMMUTABLE"
  force_delete         = true

  image_scanning_configuration {
    scan_on_push = true
  }

  encryption_configuration {
    encryption_type = "AES256"
  }

  tags = local.tags
}

resource "aws_ecr_lifecycle_policy" "application" {
  repository = aws_ecr_repository.application.name
  policy = jsonencode({
    rules = [{
      rulePriority = 1
      description  = "Retain only the five newest demo images"
      selection = {
        tagStatus   = "any"
        countType   = "imageCountMoreThan"
        countNumber = 5
      }
      action = { type = "expire" }
    }]
  })
}

resource "aws_ecs_cluster" "this" {
  name = "${var.name}-cluster"

  setting {
    name  = "containerInsights"
    value = "enabled"
  }

  tags = local.tags
}

resource "aws_cloudwatch_log_group" "query_api" {
  name              = "/ecs/${var.name}/query-api"
  retention_in_days = 7
  tags              = local.tags
}

resource "aws_cloudwatch_log_group" "command_api" {
  name              = "/ecs/${var.name}/command-api"
  retention_in_days = 7
  tags              = local.tags
}

resource "aws_cloudwatch_log_group" "worker" {
  name              = "/ecs/${var.name}/worker"
  retention_in_days = 7
  tags              = local.tags
}

resource "aws_iam_role" "execution" {
  name               = "${var.name}-ecs-execution"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.tags
}

resource "aws_iam_role_policy_attachment" "execution" {
  role       = aws_iam_role.execution.name
  policy_arn = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
}

resource "aws_iam_role_policy" "execution_secret" {
  name = "read-rds-managed-secret"
  role = aws_iam_role.execution.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [{
      Effect   = "Allow"
      Action   = "secretsmanager:GetSecretValue"
      Resource = var.database_secret_arn
    }]
  })
}

resource "aws_iam_role" "query_api" {
  name               = "${var.name}-query-api"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.tags
}

resource "aws_iam_role" "command_api" {
  name               = "${var.name}-command-api"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.tags
}

resource "aws_iam_role" "worker" {
  name               = "${var.name}-worker"
  assume_role_policy = local.ecs_task_assume_role_policy
  tags               = local.tags
}

resource "aws_iam_role_policy" "worker_messaging" {
  name = "send-and-consume-reservation-events"
  role = aws_iam_role.worker.id
  policy = jsonencode({
    Version = "2012-10-17"
    Statement = [
      {
        Sid      = "PublishOutboxEvents"
        Effect   = "Allow"
        Action   = ["sqs:SendMessage"]
        Resource = [var.expiration_queue_arn, var.notification_queue_arn]
      },
      {
        Sid      = "ConsumeWorkerQueues"
        Effect   = "Allow"
        Action   = ["sqs:ChangeMessageVisibility", "sqs:DeleteMessage", "sqs:GetQueueAttributes", "sqs:ReceiveMessage"]
        Resource = [var.expiration_queue_arn, var.notification_queue_arn]
      },
      {
        Sid      = "SendReservationEmail"
        Effect   = "Allow"
        Action   = ["ses:SendEmail", "ses:SendRawEmail"]
        Resource = ["*"]
        Condition = {
          StringEquals = { "ses:FromAddress" = var.ses_sender_email }
        }
      }
    ]
  })
}

resource "aws_lb_target_group" "query_api" {
  name        = "${var.name}-query"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    enabled             = true
    path                = "/actuator/health"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = local.tags
}

resource "aws_lb_target_group" "command_api" {
  name        = "${var.name}-command"
  port        = 8080
  protocol    = "HTTP"
  target_type = "ip"
  vpc_id      = var.vpc_id

  health_check {
    enabled             = true
    path                = "/actuator/health"
    matcher             = "200"
    healthy_threshold   = 2
    unhealthy_threshold = 3
  }

  tags = local.tags
}

resource "aws_ecs_task_definition" "query_api" {
  family                   = "${var.name}-query-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.query_api.arn

  container_definitions = jsonencode([{
    name      = "query-api"
    image     = local.image_uri
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = concat(local.common_environment, [{ name = "SPRING_PROFILES_ACTIVE", value = "query-api" }])
    secrets     = local.database_secrets
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.query_api.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])

  tags = local.tags
}

resource "aws_ecs_task_definition" "command_api" {
  family                   = "${var.name}-command-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.command_api.arn

  container_definitions = jsonencode([{
    name      = "command-api"
    image     = local.image_uri
    essential = true
    portMappings = [{
      containerPort = 8080
      protocol      = "tcp"
    }]
    environment = concat(local.common_environment, [{ name = "SPRING_PROFILES_ACTIVE", value = "command-api" }])
    secrets     = local.database_secrets
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.command_api.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])

  tags = local.tags
}

resource "aws_ecs_task_definition" "worker" {
  family                   = "${var.name}-worker"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = 256
  memory                   = 512
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.worker.arn

  container_definitions = jsonencode([{
    name      = "worker"
    image     = local.image_uri
    essential = true
    environment = concat(local.common_environment, [
      { name = "SPRING_PROFILES_ACTIVE", value = "worker" },
      { name = "OUTBOX_PUBLISHER_ENABLED", value = "true" },
      { name = "OUTBOX_PUBLISHER_EXPIRATION_QUEUE_URL", value = var.expiration_queue_url },
      { name = "OUTBOX_PUBLISHER_NOTIFICATION_QUEUE_URL", value = var.notification_queue_url },
      { name = "OUTBOX_PUBLISHER_REGION", value = var.aws_region },
      { name = "EXPIRATION_CONSUMER_ENABLED", value = "true" },
      { name = "EXPIRATION_CONSUMER_QUEUE_URL", value = var.expiration_queue_url },
      { name = "NOTIFICATION_CONSUMER_ENABLED", value = "true" },
      { name = "NOTIFICATION_CONSUMER_QUEUE_URL", value = var.notification_queue_url },
      { name = "NOTIFICATION_EMAIL_PROVIDER", value = "ses" },
      { name = "NOTIFICATION_EMAIL_FROM_ADDRESS", value = var.ses_sender_email },
      { name = "NOTIFICATION_EMAIL_REGION", value = var.aws_region }
    ])
    secrets = local.database_secrets
    logConfiguration = {
      logDriver = "awslogs"
      options = {
        awslogs-group         = aws_cloudwatch_log_group.worker.name
        awslogs-region        = var.aws_region
        awslogs-stream-prefix = "ecs"
      }
    }
  }])

  tags = local.tags
}

resource "aws_ecs_service" "query_api" {
  name                              = "query-api"
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.query_api.arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.query_api.arn
    container_name   = "query-api"
    container_port   = 8080
  }

  tags = local.tags
}

resource "aws_ecs_service" "command_api" {
  name                              = "command-api"
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.command_api.arn
  desired_count                     = 1
  launch_type                       = "FARGATE"
  health_check_grace_period_seconds = 180

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.command_api.arn
    container_name   = "command-api"
    container_port   = 8080
  }

  tags = local.tags
}

resource "aws_ecs_service" "worker" {
  name            = "worker"
  cluster         = aws_ecs_cluster.this.id
  task_definition = aws_ecs_task_definition.worker.arn
  desired_count   = 1
  launch_type     = "FARGATE"

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.private_app_subnet_ids
    security_groups  = [var.ecs_tasks_security_group_id]
    assign_public_ip = false
  }

  tags = local.tags
}
