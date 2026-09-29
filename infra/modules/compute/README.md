# Demo compute module

Creates an immutable and scan-on-push ECR repository, one ECS cluster, and three private Fargate services from the same image: `query-api`, `command-api`, and `worker`. Task definitions select the Spring profile and receive database credentials through ECS Secrets Manager references.

Only the worker task role can send or consume the two existing SQS worker queues, publish to the reservation-owner queue, and consume reservation outcomes from the inbound queue. Its policies do not overlap the external role's permissions. The worker can also send email from the configured SES identity, invoke the fixed Nova Micro foundation model, and read configured CloudWatch alarm history. Discord delivery is optional: set `discord_webhook_secret_arn` to the exact Secrets Manager ARN; leave it empty to omit secret-read permission. The worker receives only that ARN in `EXECUTIVE_SUMMARY_DISCORD_WEBHOOK_SECRET_ARN`; the URL belongs in the secret's `SecretString`, never in Terraform variables or task definitions. `executive_summary_operational_alarms` provides up to 12 CloudWatch alarm-name/friendly-label pairs. The module test uses Terraform's mocked AWS provider and creates no AWS resources.

Queue URLs reach the worker as `OUTBOX_PUBLISHER_OWNER_QUEUE_URL` and `CONFIRMATION_CONSUMER_QUEUE_URL` for the outbox publisher and future inbox consumer.
