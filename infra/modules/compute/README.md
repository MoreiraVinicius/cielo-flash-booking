# Demo compute module

Creates an immutable and scan-on-push ECR repository, one ECS cluster, and three private Fargate services from the same image: `query-api`, `command-api`, and `worker`. Task definitions select the Spring profile and receive database credentials through ECS Secrets Manager references.

Only the worker task role can send or consume the two SQS queues and send email from the configured SES identity. The module test uses Terraform's mocked AWS provider and creates no AWS resources.
