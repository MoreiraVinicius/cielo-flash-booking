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

Inventário de todos os arquivos de `docs/images/`. **Vigente** descreve o runtime local ou o contrato declarado hoje; **histórico** preserva uma demo ou evidência anterior; **alvo** descreve infraestrutura não provisionada. A legenda do README complementa os desenhos históricos.

| Arquivo | Recorte | Resultado da auditoria |
| --- | --- | --- |
| `flash-booking-confirmation-aws-components.svg` | Vigente, AWS declarada | Sequência 01–08, duas SQS direcionais com DLQ, caixa preta externa e ramos de expiração/cancelamento; sem IAM e sem alegar aplicação AWS. |
| `flash-booking-confirmation-c4-context.svg` | Vigente, C4 contexto | Responsável único resolve todas as pendências; Flash Booking decide estoque e prazo. Correto. |
| `flash-booking-confirmation-c4-containers.svg` | Vigente, C4 containers | APIs, worker, PostgreSQL e filas; inbox/outbox são dados. Correto. |
| `flash-booking-confirmation-lifecycle.svg` | Vigente, estados | Corrigido nome completo `ReservationCancellationRequested`; mantém `CANCELLATION_PENDING` até conclusão externa. |
| `flash-booking-data-model.svg` | Vigente, dados decisivos | Inbox reconhece reentrega idêntica e reapresenta resultado; responsável externo deduplica sua operação. Correto; não pretende ser ER completo. |
| `flash-booking-transactional-outbox.svg` | Vigente, publicação | Substitui a ilustração antiga: criação grava três eventos; notificação é de reserva temporária, expiração e trabalho externo vão a filas distintas. |
| `flash-booking-last-ticket.svg` | Vigente, concorrência | Corrigido o rodapé: `CONFIRMED` conserva o débito; cancelamento de confirmado libera só após reversão externa. |
| `flash-booking-sequence-reservation.svg` | Vigente, recorte da criação | Corrigidos três eventos da transação; legenda indica que `ReservationHeld` segue ao responsável na vista AWS separada. |
| `flash-booking-c4-components.svg` | Vigente, componentes locais | Mostra consumer de confirmação e quatro filas com DLQs; AWS externa não aplicada. Correto. |
| `flash-booking-hero.svg` | Vigente, apresentação | Removida contagem volátil de 94 testes; chip agora identifica `CONFIRMED`. |
| `flash-booking-idempotency.png` | Vigente, HTTP | Explica `Idempotency-Key` do comando HTTP, mecanismo distinto da inbox e da operação externa. Correto. |
| `flash-booking-data-model-before-confirmation.png` | Histórico, baseline | Modelo anterior à inbox; nome e legenda impedem uso como modelo atual. |
| `flash-booking-transactional-outbox-before-confirmation.png` | Histórico, baseline | Ilustração antiga chamava o e-mail temporário de confirmação; retirada da vista vigente do README e mantida apenas como evidência anterior. |
| `flash-booking-aws-demo.svg` | Histórico, implantação | Demo AWS aplicada, validada e destruída antes das filas externas. Correto como evidência histórica. |
| `flash-booking-aws-eventual-consistency.svg` | Histórico, duas filas originais | Recebeu rótulo visível de demo histórica; seus dois eventos de criação não descrevem o runtime atual. |
| `flash-booking-c4-demo.svg` | Histórico, C4 demo | Implantação antiga sem integração externa; recorte histórico explícito. |
| `flash-booking-spec-driven.svg` | Histórico, processo | Os 94 testes são identificados como baseline histórico da demo, não cobertura atual. |
| `flash-booking-performance.svg` | Histórico, benchmark | Medidas de baseline local com escopo histórico visível. |
| `flash-booking-edge-burst.svg` | Histórico, burst | Evidência de borda da demo com limites históricos visíveis. |
| `flash-booking-architecture-evolution.svg` | Alvo, comparação | Separa demo destruída e high-load não aplicada; fluxo de confirmação está nas vistas próprias. |
| `flash-booking-c4-high-load.svg` | Alvo, C4 high-load | Topologia projetada; não afirma deployment nem capacidade medida. |
| `flash-booking-aws-high-load.svg` | Alvo, AWS high-load | Topologia projetada; não afirma provisionamento, failover nem benchmark remoto. |

### Correções verificadas

- Contratos comparados com `CreateReservationService`, `ReservationConfirmationService`, `CancelReservationService`, consumidores SQS, roteamento outbox, Terraform e `spec.md`.
- SVGs alterados parseados como XML, renderizados em Chrome headless e inspecionados quanto a cortes, ordem das setas e textos.
- `scripts/validate-readme.ps1` deve confirmar as referências locais e exigir o novo SVG de outbox.

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
