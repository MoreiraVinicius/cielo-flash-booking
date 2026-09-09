# Demo edge and observability module

Creates the only public entrypoint: a regional REST API Gateway that requires IAM/SigV4. It connects privately through VPC Link V2 to an internal ALB. Method-specific listener rules send GET requests to `query-api` and POST/DELETE requests to `command-api`; no rule exposes actuator.

The module also creates API access logs, WAF IP rate limiting, 50%/80%/100% US$100 budget alerts, essential alarms, and a dashboard. `ApiInvokerRole` is assumable only by supplied principals and grants `execute-api:Invoke` only.
