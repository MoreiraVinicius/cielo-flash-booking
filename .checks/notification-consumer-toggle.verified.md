# Notification consumer toggle Verification

**Verdict**: PASS
**Profile**: light
**Diff range**: `3b9aac7..7b5b513`
**Round**: 1 - full
**Verifier**: independent sub-agent (author != verifier)

## Binding sources

| Source | Opened | Contradiction | Uncovered |
| --- | --- | --- | --- |
| `.tasks/notification-consumer-toggle.md` | yes | none | none; its default, false override, worker services, conditional Spring bean, and runbook sequence are covered by C1-C4. |
| `.specs/features/notification-consumer-toggle/spec.md` | yes | none | none; P1 acceptance criteria map to C1-C4. The preservation of outbox and expiration is additionally asserted by C2's Terraform proof. |

The configuration path is present: the demo forwards the boolean to compute at `infra/environments/demo/main.tf:65`, and the worker serializes it at `infra/modules/compute/main.tf:305` while retaining the two other services at `infra/modules/compute/main.tf:299` and `infra/modules/compute/main.tf:303`.

## Checks

| Check | Claim | Proof run | Evidence | Result |
| --- | --- | --- | --- | --- |
| C1 | Default worker value is `true`. | `terraform -chdir=infra/modules/compute test` — exit 0; `uses_one_image_with_separate_least_privilege_services` passed. | `infra/modules/compute/compute.tftest.hcl:78` asserts the worker's `NOTIFICATION_CONSUMER_ENABLED` is `"true"`. | PASS |
| C2 | Explicit `false` reaches the worker. | `terraform -chdir=infra/modules/compute test` — exit 0; `pauses_only_notification_consumer` passed. | `infra/modules/compute/compute.tftest.hcl:111` supplies `notification_consumer_enabled = false`; `infra/modules/compute/compute.tftest.hcl:117` asserts the worker value is `"false"`. `infra/modules/compute/compute.tftest.hcl:122` also asserts outbox publisher and expiration remain `"true"`. | PASS |
| C3 | A disabled worker profile does not create `SqsReservationCreatedConsumer`. | The declared `./mvnw.cmd -Dtest=SqsReservationCreatedConsumerConfigurationTest test` could not start Maven because the wrapper dereferences a null `$HOME` link target in this environment. The same test was then run at HEAD with the wrapper's cached Maven 3.9.10 distribution and explicit existing local repository: exit 0, 1 test run, 0 failures/errors. | `src/test/java/com/cielo/flashbooking/notification/email/SqsReservationCreatedConsumerConfigurationTest.java:13-14` sets worker plus `notification.consumer.enabled=false`; `src/test/java/com/cielo/flashbooking/notification/email/SqsReservationCreatedConsumerConfigurationTest.java:18` asserts `doesNotHaveBean(SqsReservationCreatedConsumer.class)`. The conditional production configuration is at `src/main/java/com/cielo/flashbooking/notification/email/SqsReservationCreatedConsumerConfiguration.java:20-23`. | PASS |
| C4 | Runbook pauses before seeding, restores afterward, and says the pause does not remove SQS messages. | Declared PowerShell `Select-String` proof — exit 0. | `docs/pausar-notificacoes-semeadura.md:8` sets `false`; `docs/pausar-notificacoes-semeadura.md:21` restores `true`; `docs/pausar-notificacoes-semeadura.md:24` states `A pausa não remove mensagens da SQS`. | PASS |

## Profile-limited checks

`light` requires the binding-source comparison and proof execution only. The checklist has no `Test policy` section. Coverage joins and fault injection were not required and were not run.

## Gate

- `terraform -chdir=infra/modules/compute test` — 2 passed, 0 failed. Terraform also emitted a non-fatal warning that its user-level CLI configuration directory was inaccessible.
- Spring targeted test — 1 run, 0 failures, 0 errors, using the cached Maven distribution after the declared wrapper bootstrap failure described under C3.
- Runbook string proof — passed.
