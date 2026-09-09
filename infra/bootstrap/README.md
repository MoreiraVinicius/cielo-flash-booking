# Terraform state bootstrap

This module creates the private S3 bucket that stores Terraform state. It has versioning, default SSE-S3 encryption, ownership enforcement, public-access blocks, and `prevent_destroy`.

Run `terraform init`, then provide a globally unique `state_bucket_name` through a local ignored tfvars file. Apply only after the demo cost and authorization gates are satisfied.
