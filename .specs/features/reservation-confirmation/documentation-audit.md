# Auditoria da documentação e dos desenhos da confirmação externa

**Situação:** o ciclo de confirmação está implementado no runtime local. Compose/LocalStack e testes integram as duas filas SQS com um responsável simulado. Os recursos AWS correspondentes estão declarados em Terraform, mas não foram aplicados; não há módulo externo real nem pagamentos neste sistema.

## Fontes e contratos

| Artefato | Estado e responsabilidade |
| --- | --- |
| `Case BackEnd 1.md` | Fonte de requisitos do case: cinco endpoints, reserva temporária, oversell zero, expiração e idempotência. A confirmação externa amplia o ciclo sem retirar endpoints nem introduzir pagamentos. |
| `.specs/STATE.md` | AD-031 rege o ciclo de confirmação; AD-007 está supersedida para as transições do agregado. AD-032 é independente e preservado. |
| `.specs/CONTEXT.md` | Glossário contém `CONFIRMED`, `CANCELLATION_PENDING`, `ReservationHeld`, resultados e cancelamento externo. |
| `.specs/features/reservation-confirmation/{spec,context,design,tasks}.md` | Contrato atual, responsabilidades, inbox/outbox, decisão temporal, estados, integração e evidências da implementação local. |
| `.specs/features/flash-booking-demo/*` | Descreve a implementação compartilhada do domínio e preserva limites de deployment da demo. |
| `.specs/features/flash-booking-high-load/*` | Topologia ainda não aplicada; usa o mesmo ciclo e schema sem alegar capacidade remota ou failover. |
| `.specs/features/dynamic-fake-load/*` | Auditoria inclui `PENDING`, `CONFIRMED` e `CANCELLATION_PENDING` no inventário comprometido. |
| `.specs/features/event-executive-summary/*` | Resume reservas válidas, canceladas e expiradas no corte de `endsAt`; nunca chama reserva de compra. |
| `README.md` | Explica ciclo executável, cinco rotas, C4, fluxo temporal e distinção entre runtime local implementado e AWS não aplicada. |
| `docs/rodar-localmente.md` | Inicia Compose/LocalStack e informa o comando de integração que simula o único responsável sem pagamento. |
| `postman/README.md` | Explica contratos HTTP e limitações da coleção; a integração SQS completa é verificada pelo teste Java/Testcontainers. |

## Diagramas e imagens

| Artefato | Representação atual |
| --- | --- |
| `flash-booking-confirmation-c4-context.svg` | C4 Context: pessoa, Flash Booking e responsável único externo; runtime local implementado e AWS não aplicada. |
| `flash-booking-confirmation-c4-containers.svg` | C4 Container: APIs, worker, PostgreSQL e filas direcionais; outbox/inbox são dados, não containers; sem SNS. |
| `flash-booking-confirmation-aws-components.svg` | Recursos AWS declarados para confirmação: borda API, ECS Fargate, RDS PostgreSQL, duas SQS com DLQ e único responsável externo opaco; não representa implantação ativa nem detalha autenticação. |
| `flash-booking-confirmation-lifecycle.svg` | Estados, lock e relógio PostgreSQL, duplicidade, expiração, rejeição e cancelamento pendente. |
| `flash-booking-data-model.svg` | Vista C4 de containers e dados decisivos: responsável externo em caixa preta, filas direcionais, worker, reserva/estoque, inbox e outbox; distingue a reentrega idêntica da resolução da idempotência da operação externa. Não é um ER completo. |
| `flash-booking-data-model-before-confirmation.png` | Imagem preservada como baseline anterior à inbox e aos metadados de confirmação; não é o modelo vigente. |
| `flash-booking-transactional-outbox.png` | Exemplo didático da criação; o texto do README explica que o padrão também publica os novos tipos de integração. |
| `flash-booking-last-ticket.svg` | Escrita condicional de estoque; confirmação não debita novamente. |
| `flash-booking-sequence-reservation.svg` | Sequência da criação, notificação, expiração e consulta; limitada a esse fluxo e complementada pelo lifecycle da confirmação. |
| `flash-booking-c4-demo.svg`, `flash-booking-c4-components.svg` | Deployment/componentes da demo; descrições distinguem esses recortes das filas da confirmação. |
| `flash-booking-aws-demo.svg` | Topologia historicamente aplicada e destruída; não representa as novas filas como deployed. |
| `flash-booking-aws-eventual-consistency.svg` | Consistência do runtime demonstrado; mensagens de integração permanecem no C4 de confirmação. |
| `flash-booking-architecture-evolution.svg`, `flash-booking-c4-high-load.svg`, `flash-booking-aws-high-load.svg` | Alvo high-load não provisionado; não é evidência de deployment nem benchmark remoto. |
| `flash-booking-hero.svg`, `flash-booking-idempotency.png`, `flash-booking-spec-driven.svg`, `flash-booking-performance.svg`, `flash-booking-edge-burst.svg` | Imagens de apresentação, idempotência HTTP e evidências históricas; claims e limitações continuam explícitos. |

## Infraestrutura e integração

| Área | Evidência e limite |
| --- | --- |
| `compose.yaml` e LocalStack | Representam as filas locais, inbox/outbox e worker. O fluxo E2E de teste simula o responsável externo. |
| `infra/modules/{compute,data-plane}` | Terraform declara duas filas direcionais, DLQs e IAM delimitado; nenhum apply da nova arquitetura foi executado e nenhuma role externa real foi vinculada. |
| Filas existentes | Notificação e expiração mantêm seus contratos e propósitos; integração não reutiliza essas filas. |
| Responsabilidade externa | Um único módulo resolve todas as pendências; réplicas consomem a mesma fila de trabalho e precisam de deduplicação durável. Flash Booking não verifica pagamento nem acompanha compensação. |
| Postman | Demonstra HTTP, não publicação SQS. A integração de mensagens é coberta por `SqsReservationConfirmationConsumerIT`. |

## Evidência de reconciliação

- A suíte focada de T07 passou: 98 unitários e 23 integrações de `SqsReservationConfirmationConsumerIT`, `ReservationQueryControllerIT` e `ExecutiveSummaryFactsIT`.
- O teste de integração simula `ReservationHeld`, confirmação externa, `CONFIRMED`, solicitação de cancelamento e conclusão correlacionada, sem pagamento.
- A auditoria do resumo executivo valida `confirmed_at` e o corte de `endsAt`; a auditoria da carga considera todos os estados que retêm inventário.
- `scripts/validate-readme.ps1`, validadores TLC, renderização e inspeção visual dos SVGs, e `git diff --check` são gates documentais da tarefa.
- A aplicação AWS, integração com um responsável real, pagamentos e a topologia high-load não são afirmados nem incluídos nesta evidência.
