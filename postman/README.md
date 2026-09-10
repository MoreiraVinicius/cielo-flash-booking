# AWS demo collection

Import `flash-booking-aws.postman_collection.json` into Postman and run it in collection order while the demo infrastructure is active.

The collection points to the deployed API Gateway demo URL and signs authenticated requests with AWS Signature Version 4. Before running it, assume the output role `api_invoker_role_arn` and set these collection variables from the resulting **temporary** session only:

- `awsAccessKeyId`
- `awsSecretAccessKey`
- `awsSessionToken`

Do not commit populated credentials. The collection intentionally leaves all credential values empty.

The run covers the five case endpoints, IAM enforcement, event and reservation lifecycle, persistent idempotency, capacity conflict, capacity return on cancellation, and the standard error contract. It sends one reservation notification to the verified `customerEmail` address.

The URL is a short-lived demo endpoint. If Terraform creates a new API later, replace `baseUrl` with the new `api_invoke_url` output before running the collection.
