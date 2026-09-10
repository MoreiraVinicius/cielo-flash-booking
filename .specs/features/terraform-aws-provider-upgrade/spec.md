# Terraform e AWS Provider Upgrade Specification

## Problem Statement

O repositório usa Terraform 1.9.6 de 32 bits e AWS Provider 5.100.0, o que impede o uso do provider 6.x. A infraestrutura deve usar as versões estáveis mais recentes, de 64 bits, sem alterar recursos remotos sem um plano aprovado.

## Goals

- [ ] Executar a infraestrutura localmente com Terraform 1.16.1 para Windows amd64.
- [ ] Fixar a dependência AWS na versão estável 6.64.0 e validar a configuração compatível.
- [ ] Preservar o desenho REST API + VPC Link V2 + ALB e impedir `apply` remoto durante a atualização.

## Out of Scope

| Feature | Reason |
| --- | --- |
| Aplicar, destruir ou alterar recursos na AWS | Não há credenciais nem autorização de operação remota. |
| Adotar Terraform 1.17 alpha | Pré-lançamentos não são a versão estável mais recente. |
| Reestruturar módulos sem necessidade do upgrade | A solicitação é atualização de toolchain e provider. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- |
| Versão do Terraform | 1.16.1 estável, Windows amd64 | É a última versão estável publicada em 2 de setembro de 2026; 1.17 é alpha. | y |
| Versão do provider | `hashicorp/aws` 6.64.0 | É a versão estável mais recente resolvida pelo Registry em 10 de setembro de 2026. | y |
| Instalação local | Atualizar o Terraform existente gerenciado pelo Chocolatey, se o pacote disponibilizar 1.16.1 | Preserva o mecanismo pelo qual o binário atual foi instalado. | y |
| Evidência remota | Apenas `fmt`, `init -upgrade`, `validate` e testes locais; registrar a ausência de `plan` autenticado | O projeto continua sem credenciais AWS e sem autorização de apply. | y |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Toolchain atual ⭐ MVP

**User Story**: Como responsável pela infraestrutura, quero executar Terraform estável de 64 bits para que o provider AWS atual seja suportado.

**Why P1**: O binário de 32 bits bloqueia o provider 6.x.

**Acceptance Criteria**:

1. WHEN `terraform version` for the configured executable is run THEN the toolchain SHALL report Terraform `v1.16.1` on `windows_amd64`.
2. WHILE upgrading the local toolchain THEN the system SHALL not execute `terraform apply` or `terraform destroy`.

**Independent Test**: Execute `terraform version` using the installed executable and inspect its platform.

### P1: Provider AWS atual e compatível

**User Story**: Como responsável pela infraestrutura, quero que todos os módulos usem o provider AWS estável atual para receber correções e compatibilidade atualizada.

**Why P1**: O provider 5.x não recebe evolução regular após a chegada da linha 6.x.

**Acceptance Criteria**:

1. WHEN Terraform initializes each root module THEN the dependency lock file SHALL select `hashicorp/aws` `6.64.0`.
2. WHEN a REST API integration uses VPC Link V2 and an ALB THEN the configuration SHALL set `integration_target` to that ALB ARN.
3. WHEN the updated root modules are validated locally THEN Terraform SHALL complete `fmt -check` and `validate` without configuration diagnostics.
4. IF an authenticated AWS plan cannot be run THEN the upgrade evidence SHALL state that remote compatibility remains unverified.

**Independent Test**: Run `terraform init -upgrade`, `terraform fmt -check -recursive`, `terraform validate`, and module tests with no AWS apply.

## Edge Cases

- IF the Chocolatey package does not publish Terraform 1.16.1 THEN the system SHALL stop before changing PATH and report the supported official-install alternative.
- IF provider 6 reports a migration diagnostic THEN the system SHALL update only the documented incompatible configuration and repeat local validation.
- IF a remote plan requires unavailable credentials THEN the system SHALL not synthesize credentials or bypass authentication.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| TFUP-01 | P1: Toolchain atual | Execute | Verified |
| TFUP-02 | P1: Provider AWS atual e compatível | Execute | Verified |
| TFUP-03 | P1: Provider AWS atual e compatível | Execute | Implementing |
| TFUP-04 | P1: Provider AWS atual e compatível | Verify | Pending |

**Coverage:** 4 total, 0 mapped to tasks, 4 unmapped.

## Success Criteria

- [ ] A versão local configurada é Terraform 1.16.1 amd64.
- [ ] Os dois root modules têm lockfiles que selecionam AWS Provider 6.62.0.
- [ ] Nenhuma operação remota é disparada durante a atualização.
