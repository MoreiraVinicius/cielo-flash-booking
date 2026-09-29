output "api_invoke_url" { value = module.edge_observability.api_invoke_url }
output "api_invoker_role_arn" { value = module.edge_observability.api_invoker_role_arn }
output "ecr_repository_url" { value = module.compute.ecr_repository_url }
output "reservation_to_owner_queue_url" {
  description = "SQS URL the external reservation owner consumes."
  value       = module.data_plane.reservation_to_owner_queue_url
}
output "reservation_from_owner_queue_url" {
  description = "SQS URL the external reservation owner uses to submit confirmation and cancellation outcomes."
  value       = module.data_plane.reservation_from_owner_queue_url
}
