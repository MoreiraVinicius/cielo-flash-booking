# Demo data-plane module

Creates private, encrypted RDS PostgreSQL and ElastiCache for Valkey resources in isolated subnets. RDS generates and manages its master password in AWS Secrets Manager; Terraform never receives or stores the password value. It also creates independent encrypted SQS queues and DLQs for expiration, notifications, and both directions of reservation integration, plus the SES sender identity that must be verified before a demo send.

`reservation-to-owner` is consumed by the one external owner; `reservation-from-owner` receives its confirmation and cancellation outcomes. Each has a separate DLQ, 20-second long polling, and SQS-managed encryption. Set `reservation_owner_role_arn` to grant that exact IAM role consume access to the outgoing queue and `SendMessage` to the incoming queue. If unset, no external principal is granted access. The owner role's identity policy is managed by its own system; cross-account use requires both sides to allow the access.

The module test runs against Terraform's mocked AWS provider and creates no AWS resources.
