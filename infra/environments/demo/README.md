# AWS demo environment

This composition creates the full demo topology from the network, data-plane, compute, and edge modules. It intentionally has no backend block: after applying `infra/bootstrap` once, initialize with a non-versioned backend configuration file that points to the private state bucket.

Copy `demo.tfvars.example` to an ignored `demo.tfvars`, replace every placeholder, and run `terraform init -backend=false` plus `terraform validate` without credentials. An actual `plan` or `apply` requires temporary AWS credentials and explicit deployment authorization.
