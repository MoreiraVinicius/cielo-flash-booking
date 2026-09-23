locals {
  az_indexes = toset(["0", "1"])

  tags = {
    ManagedBy = "Terraform"
    Project   = var.name
  }
}

resource "aws_vpc" "this" {
  cidr_block           = var.vpc_cidr
  enable_dns_hostnames = true
  enable_dns_support   = true

  tags = merge(local.tags, { Name = "${var.name}-vpc" })
}

resource "aws_internet_gateway" "this" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-igw" })
}

resource "aws_subnet" "public" {
  for_each = local.az_indexes

  vpc_id                  = aws_vpc.this.id
  availability_zone       = var.availability_zones[tonumber(each.key)]
  cidr_block              = var.public_subnet_cidrs[tonumber(each.key)]
  map_public_ip_on_launch = false

  tags = merge(local.tags, { Name = "${var.name}-public-${each.key}" })
}

resource "aws_subnet" "private_app" {
  for_each = local.az_indexes

  vpc_id            = aws_vpc.this.id
  availability_zone = var.availability_zones[tonumber(each.key)]
  cidr_block        = var.private_app_subnet_cidrs[tonumber(each.key)]

  tags = merge(local.tags, { Name = "${var.name}-app-${each.key}" })
}

resource "aws_subnet" "isolated_data" {
  for_each = local.az_indexes

  vpc_id            = aws_vpc.this.id
  availability_zone = var.availability_zones[tonumber(each.key)]
  cidr_block        = var.isolated_data_subnet_cidrs[tonumber(each.key)]

  tags = merge(local.tags, { Name = "${var.name}-data-${each.key}" })
}

resource "aws_route_table" "public" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block = "0.0.0.0/0"
    gateway_id = aws_internet_gateway.this.id
  }

  tags = merge(local.tags, { Name = "${var.name}-public" })
}

resource "aws_route_table_association" "public" {
  for_each = aws_subnet.public

  subnet_id      = each.value.id
  route_table_id = aws_route_table.public.id
}

resource "aws_eip" "nat" {
  domain = "vpc"

  tags = merge(local.tags, { Name = "${var.name}-nat" })
}

resource "aws_nat_gateway" "this" {
  allocation_id = aws_eip.nat.id
  subnet_id     = aws_subnet.public["0"].id

  depends_on = [aws_internet_gateway.this]

  tags = merge(local.tags, { Name = "${var.name}-nat" })
}

resource "aws_route_table" "private_app" {
  vpc_id = aws_vpc.this.id

  route {
    cidr_block     = "0.0.0.0/0"
    nat_gateway_id = aws_nat_gateway.this.id
  }

  tags = merge(local.tags, { Name = "${var.name}-app" })
}

resource "aws_route_table_association" "private_app" {
  for_each = aws_subnet.private_app

  subnet_id      = each.value.id
  route_table_id = aws_route_table.private_app.id
}

resource "aws_route_table" "isolated_data" {
  vpc_id = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-data" })
}

resource "aws_route_table_association" "isolated_data" {
  for_each = aws_subnet.isolated_data

  subnet_id      = each.value.id
  route_table_id = aws_route_table.isolated_data.id
}

resource "aws_security_group" "vpc_link" {
  name_prefix = "${var.name}-vpclink-"
  description = "Allows the private API Gateway VPC Link to reach the internal ALB."
  vpc_id      = aws_vpc.this.id

  egress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = [var.vpc_cidr]
  }

  tags = merge(local.tags, { Name = "${var.name}-vpclink" })
}

resource "aws_security_group" "alb" {
  name_prefix = "${var.name}-alb-"
  description = "Allows only the VPC Link to reach the internal ALB."
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-alb" })
}

resource "aws_security_group" "ecs_tasks" {
  name_prefix = "${var.name}-ecs-"
  description = "Allows only the internal ALB to reach application tasks."
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-ecs" })
}

resource "aws_security_group" "rds" {
  name_prefix = "${var.name}-rds-"
  description = "Allows only ECS tasks to reach PostgreSQL."
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-rds" })
}

resource "aws_security_group" "valkey" {
  name_prefix = "${var.name}-valkey-"
  description = "Allows only ECS tasks to reach Valkey."
  vpc_id      = aws_vpc.this.id

  tags = merge(local.tags, { Name = "${var.name}-valkey" })
}

resource "aws_vpc_security_group_ingress_rule" "alb_from_vpc_link" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.vpc_link.id
  from_port                    = 80
  to_port                      = 80
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "alb_to_ecs" {
  security_group_id            = aws_security_group.alb.id
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  from_port                    = var.application_port
  to_port                      = var.application_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "ecs_from_alb" {
  security_group_id            = aws_security_group.ecs_tasks.id
  referenced_security_group_id = aws_security_group.alb.id
  from_port                    = var.application_port
  to_port                      = var.application_port
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_https" {
  security_group_id = aws_security_group.ecs_tasks.id
  cidr_ipv4         = "0.0.0.0/0"
  from_port         = 443
  to_port           = 443
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_to_rds" {
  security_group_id            = aws_security_group.ecs_tasks.id
  referenced_security_group_id = aws_security_group.rds.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_egress_rule" "ecs_to_valkey" {
  security_group_id            = aws_security_group.ecs_tasks.id
  referenced_security_group_id = aws_security_group.valkey.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_ecs" {
  security_group_id            = aws_security_group.rds.id
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  from_port                    = 5432
  to_port                      = 5432
  ip_protocol                  = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "rds_from_administrator" {
  count = var.rds_administrative_cidr == null ? 0 : 1

  security_group_id = aws_security_group.rds.id
  cidr_ipv4         = var.rds_administrative_cidr
  from_port         = 5432
  to_port           = 5432
  ip_protocol       = "tcp"
}

resource "aws_vpc_security_group_ingress_rule" "valkey_from_ecs" {
  security_group_id            = aws_security_group.valkey.id
  referenced_security_group_id = aws_security_group.ecs_tasks.id
  from_port                    = 6379
  to_port                      = 6379
  ip_protocol                  = "tcp"
}
