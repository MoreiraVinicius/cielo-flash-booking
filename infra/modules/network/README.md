# Demo network module

Creates a two-AZ VPC with public, private-application, and isolated-data subnet tiers. A single NAT gateway keeps the cost bounded for the short-lived demo. The module deliberately provides no public IPs to workloads and exposes security-group outputs for the data, compute, and edge modules.

`network.tftest.hcl` uses Terraform's mocked AWS provider, so `terraform test` checks the topology without credentials or resource creation.
