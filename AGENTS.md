# Spring skill routing

## Specification source of truth

Treat `.specs/` as the authoritative description of the current version 1 system. Every change to behavior, architecture, contracts, infrastructure, or verification must update the affected spec, context, design, tasks, validation, and project decisions in the same work.

Write specification artifacts as the system is intended to exist now, not as a changelog or migration narrative. Git owns the history. Keep `.specs/` complete enough that another engineer or agent can reconstruct the project and its verification strategy without relying on prior conversations.

Apply the repository decisions in `.specs/STATE.md` before any external skill. `java-spring-engineering` remains the baseline for all Java and Spring changes.

Use each external Spring Boot 3 skill only for its bounded concern:

- `configuration-properties`: typed external configuration and startup validation.
- `spring-data-jpa`: entity mapping, repositories, JPA queries, projections, pagination, and N+1 analysis.
- `transactional-patterns`: transaction boundaries, propagation, self-invocation, and after-commit work.
- `flyway-migrations`: migration workflow; use `design-postgres-tables` for PostgreSQL schema, integrity, and index decisions.
- `testing-pyramid`: test level and Spring test-slice selection.

The application currently authenticates at API Gateway through IAM/SigV4. When an authentication design is requested, present both paths clearly before implementation: `spring-security-jwt` for first-party access and refresh tokens issued by this application; `oauth2-resource-server` for tokens issued by a third-party authorization server. State the issuer, token lifecycle, operational ownership, and migration impact for each path. Apply only the selected path after an explicit architecture decision.

Resolve instruction conflicts in this order: direct user request; repository decisions and local guidance; installed local skills; external skills. Preserve the existing project contract and surface any unresolved conflict before changing implementation.
