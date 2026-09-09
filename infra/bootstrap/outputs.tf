output "state_bucket_name" {
  description = "Bucket name to configure in the environment Terraform backend."
  value       = aws_s3_bucket.terraform_state.id
}
