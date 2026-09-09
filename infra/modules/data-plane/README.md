# Demo data-plane module

Creates private, encrypted RDS PostgreSQL and ElastiCache for Valkey resources in isolated subnets. RDS generates and manages its master password in AWS Secrets Manager; Terraform never receives or stores the password value. It also creates independent encrypted SQS queues and DLQs for expiration and notifications, plus the SES sender identity that must be verified before a demo send.

The module test runs against Terraform's mocked AWS provider and creates no AWS resources.
