output "api_invoke_url" {
  description = "Regional API Gateway URL; callers must use SigV4 with ApiInvokerRole."
  value       = "https://${aws_api_gateway_rest_api.this.id}.execute-api.${var.aws_region}.amazonaws.com/${aws_api_gateway_stage.demo.stage_name}"
}

output "api_invoker_role_arn" { value = aws_iam_role.api_invoker.arn }
output "internal_alb_arn" { value = aws_lb.internal.arn }
