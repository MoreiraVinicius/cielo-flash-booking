provider "aws" {
  region  = var.aws_region
  profile = var.aws_profile

  default_tags {
    tags = {
      Environment = "demo"
      ManagedBy   = "Terraform"
      Project     = var.name
    }
  }
}

resource "aws_sns_topic" "operational_alerts" {
  name = "${var.name}-operational-alerts"
}

resource "aws_sns_topic" "budget_emergency" {
  name = "${var.name}-budget-emergency"
}

resource "aws_sns_topic_subscription" "operational_email" {
  topic_arn = aws_sns_topic.operational_alerts.arn
  protocol  = "email"
  endpoint  = var.budget_alert_email
}

module "network" {
  source                         = "../../modules/network"
  name                           = var.name
  availability_zones             = var.availability_zones
  rds_administrative_cidr        = var.database_administrative_access_enabled ? var.database_administrative_cidr : null
  data_subnets_publicly_routable = var.database_administrative_access_enabled
}

module "data_plane" {
  source                   = "../../modules/data-plane"
  name                     = var.name
  vpc_id                   = module.network.vpc_id
  isolated_data_subnet_ids = module.network.isolated_data_subnet_ids
  public_access_enabled    = var.database_administrative_access_enabled
  rds_security_group_id    = module.network.rds_security_group_id
  valkey_security_group_id = module.network.valkey_security_group_id
  ses_sender_email         = var.ses_sender_email
  alarm_topic_arn          = aws_sns_topic.operational_alerts.arn
}

module "compute" {
  source                      = "../../modules/compute"
  name                        = var.name
  aws_region                  = var.aws_region
  vpc_id                      = module.network.vpc_id
  private_app_subnet_ids      = module.network.private_app_subnet_ids
  ecs_tasks_security_group_id = module.network.ecs_tasks_security_group_id
  image_tag                   = var.image_tag
  database_host               = module.data_plane.database_host
  database_port               = module.data_plane.database_port
  database_secret_arn         = module.data_plane.database_secret_arn
  valkey_primary_endpoint     = module.data_plane.valkey_primary_endpoint
  valkey_port                 = module.data_plane.valkey_port
  expiration_queue_arn        = module.data_plane.expiration_queue_arn
  expiration_queue_url        = module.data_plane.expiration_queue_url
  notification_queue_arn      = module.data_plane.notification_queue_arn
  notification_queue_url      = module.data_plane.notification_queue_url
  ses_sender_email            = module.data_plane.ses_sender_email
  alarm_topic_arn             = aws_sns_topic.operational_alerts.arn
}

module "edge_observability" {
  source                     = "../../modules/edge-observability"
  name                       = var.name
  aws_region                 = var.aws_region
  vpc_id                     = module.network.vpc_id
  private_app_subnet_ids     = module.network.private_app_subnet_ids
  vpc_link_security_group_id = module.network.vpc_link_security_group_id
  alb_security_group_id      = module.network.alb_security_group_id
  query_target_group_arn     = module.compute.query_target_group_arn
  command_target_group_arn   = module.compute.command_target_group_arn
  trusted_principal_arns     = var.trusted_principal_arns
  allowed_cidrs              = var.allowed_cidrs
  budget_alert_email         = var.budget_alert_email
  budget_emergency_topic_arn = aws_sns_topic.budget_emergency.arn
  ecs_cluster_arn            = module.compute.ecs_cluster_arn
  ecs_cluster_name           = module.compute.ecs_cluster_name
  ecs_service_names          = [module.compute.query_service_name, module.compute.command_service_name, module.compute.worker_service_name]
  ecs_service_arns           = module.compute.ecs_service_arns
  ecs_scalable_target_arns   = module.compute.ecs_scalable_target_arns
  database_identifier        = module.data_plane.database_identifier
  database_arn               = module.data_plane.database_arn
  alarm_topic_arn            = aws_sns_topic.operational_alerts.arn
}
