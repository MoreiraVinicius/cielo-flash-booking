output "ecr_repository_url" { value = aws_ecr_repository.application.repository_url }
output "ecs_cluster_arn" { value = aws_ecs_cluster.this.arn }
output "query_target_group_arn" { value = aws_lb_target_group.query_api.arn }
output "command_target_group_arn" { value = aws_lb_target_group.command_api.arn }
output "query_service_name" { value = aws_ecs_service.query_api.name }
output "command_service_name" { value = aws_ecs_service.command_api.name }
output "worker_service_name" { value = aws_ecs_service.worker.name }
