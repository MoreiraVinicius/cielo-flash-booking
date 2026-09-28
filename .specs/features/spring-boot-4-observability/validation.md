# Spring Boot 4 e observabilidade — validação independente

**Date:** 2026-09-28  
**Spec:** `.specs/features/spring-boot-4-observability/spec.md`  
**Diff range:** `8c2d159..a61f85e` (`codex/spring-boot-4-observability`)  
**Verifier:** sub-agent independente (autor ≠ verifier)

## Validation: PASS

## Veredito

**PASS — 17/17 critérios de aceitação cobertos por evidência.** Os gates estruturais e de build passaram; os dois smokes Compose foram reproduzidos em projetos isolados e limpos. A integração AWS segue apenas planejada e não foi aplicada. Este relatório valida a implementação local; não autoriza deploy nem, por si só, marca a feature como completa.

## Conclusão das tarefas

| Tarefas | Resultado | Evidência |
| --- | --- | --- |
| T1–T15 | PASS — marcadas Complete pelo implementador | `.specs/features/spring-boot-4-observability/tasks.md:60-229`; validação estrutural abaixo não apontou erros nem warnings. |

## Critérios de aceitação (17)

| ID | Resultado esperado na spec | Evidência independente (`arquivo:linha` + resultado) | Status |
| --- | --- | --- | --- |
| BOOT4-01.1 | Parent Boot 4.0.8 e Java 21 | `pom.xml:7-22` declara `spring-boot-starter-parent:4.0.8` e `java.version=21`; compilação do gate usou `javac --release 21`. | PASS |
| BOOT4-01.2 | Suítes rápidas e integração passam sem exclusões ou enfraquecimento | `target/surefire-reports` registra 56 testes, 0 failures/errors/skips; `target/failsafe-reports` registra 69, 0 failures/errors/skips. Comando independente `./mvnw.cmd --batch-mode -Dmaven.repo.local=C:\Users\vinic\.m2\repository -Pintegration clean verify` terminou `BUILD SUCCESS`, exit 0. A contagem baseline em `tasks.md:60-73` era 122 (54+68); atual é 125 (+3). | PASS |
| BOOT4-01.3 | `query-api`, `command-api` e `worker` iniciam saudáveis | `compose.yaml` declara os serviços e respectivos healthchecks; `scripts/compose-smoke.ps1:18-52` espera health `UP` de query e command. Compose isolado `flash-booking-boot4-smoke` subiu e ficou saudável para os três serviços (`docker compose ... up -d --wait`, exit 0). | PASS |
| BOOT4-01.4 | Cinco operações HTTP mantêm status, headers e payloads | `EventControllerIT.java:75-88,132-139` verifica criação/leitura de evento, status e campos; `ReservationControllerIT.java:68-82` verifica criação da reserva, `201` e payload; `ReservationQueryControllerIT.java:62-92,120-125` verifica leitura/cancelamento e payload terminal. Smoke Compose também criou e consultou evento/reserva com sucesso. | PASS |
| BOOT4-01.5 | Invariantes de concorrência, idempotência, outbox e expiração preservadas em PostgreSQL | `InventoryConcurrencyIT.java` contém cenários concorrentes; `PersistentIdempotencyIT.java` valida persistência/replay; `InitialSchemaIT.java:39-104` valida schema e constraints; `ReservationDeadlineIT.java:106-184` exercita cancelamento/expiração concorrentes; `OutboxSqsPublisherIT.java` exercita publicação/retry. As integrações correspondentes passaram no gate (zero falhas). | PASS |
| BOOT4-02.1 | Sem exportação OTLP de métricas, traces ou logs no padrão | `application.yml:27-49` desliga os três exportadores e limita Actuator a health; `ObservabilityConfigurationTest.java:15-24` afirma `false` para os quatro switches. Mutação em scratch que ligou `management.otlp.metrics.export.enabled` foi morta: o teste falhou especificamente em `ObservabilityConfigurationTest.java:19`. | PASS |
| BOOT4-02.2 | Perfil local exporta métricas HTTP e traces para endpoint OTLP explícito | `application-observability.yml:1-26` ativa OTLP, configura endpoints HTTP `localhost:4318` e sampling local; `ObservabilityConfigurationTest.java:27-40` verifica enabled, endpoints e sampling. Execução independente de `scripts/observability-smoke.ps1` terminou com “HTTP trace and metrics received”; collector e aplicação foram removidos pelo script. | PASS |
| BOOT4-02.3 | `X-Correlation-ID` continua aparecendo em logs no formato documentado | `ApiExceptionHandlerIT.java:85-102` envia `unexpected-request` e afirma que a mensagem registrada contém `correlationId=unexpected-request`; resposta mantém o mesmo header/identificador. | PASS |
| BOOT4-02.4 | Collector indisponível não converte falha de telemetria em falha de negócio | `ObservabilityExportResilienceIT.java:20-32,55-78` aponta métricas e traces a `127.0.0.1:1`, espera `201` e confere disponibilidade `4` e uma reserva persistida. O Failsafe report dessa classe registra 1 teste, 0 falhas/erros/skips. | PASS |
| BOOT4-02.5 | Docs não afirmam trace outbox/SQS contínuo nem exportação OTLP de logs | `design.md:30-32` delimita HTTP, stdout e ausência de contexto outbox/SQS; `aws-plan.md:41,57` mantém logs em `awslogs` e exclui correlação assíncrona; `.specs/STATE.md:215-219` repete os limites ativos. | PASS |
| BOOT4-03.1 | AD-002 supersedida e AD-029 ativa | `.specs/STATE.md:12-16` marca AD-002 como `superseded by AD-029`; `.specs/STATE.md:215-222` registra runtime Boot 4.0.8/Jackson 3 e limites de telemetria. | PASS |
| BOOT4-03.2 | Versões atuais e rótulos históricos distinguem runtime atual de desenhos não aplicados | `README.md:22-30` identifica Boot 4.0.8 e high-load não implementado; `docs/images/flash-booking-c4-high-load.svg:103` nomeia Boot 4.0.8 como “alvo não aplicado”; `docs/images/flash-booking-aws-demo.svg:23` conserva aviso de evidência histórica. A inspeção foi de conteúdo/XML; não foi feita renderização pixel a pixel. | PASS |
| BOOT4-03.3 | Plano AWS diz não aplicado e preserva dashboards/demo existentes | `.specs/features/spring-boot-4-observability/aws-plan.md:1-7,13-15,41,76-80` identifica proposta sem alterações AWS, mantém AD-023/AD-024 e descreve relação/rollout; `.specs/STATE.md:218-219` mantém a mesma decisão. Nenhuma chamada Terraform/AWS foi feita. | PASS |
| BOOT4-03.4 | Links de assets resolvem e SVG alterado é XML válido | `scripts/validate-readme.ps1` terminou `PASS` (3 imagens README, 26 referências locais, 3 cenários); parse independente de `docs/images/flash-booking-c4-high-load.svg` com `[xml]` reconheceu raiz `svg`. `validate_spec.py` e `validate_tasks.py` também: 0 errors/0 warnings. | PASS |
| BOOT4-04.1 | Plano descreve origem OTLP, coletor ECS, destinos e relação com dashboards | `aws-plan.md:5-29` recomenda sidecar CloudWatch Agent, OTLP/HTTP, CloudWatch OTel Metrics e X-Ray/Transaction Search; `aws-plan.md:13-15,76,78` preserva os dashboards e define comparação no canário. | PASS |
| BOOT4-04.2 | Plano define IAM mínimo, cardinalidade, sampling, retenção e custos potenciais | `aws-plan.md:31-41` discute task roles e ações IAM; `aws-plan.md:53-59` especifica sampling, tags permitidas/PII e retenção/erro; `aws-plan.md:63-71` documenta componentes de custo e guardrails. | PASS |
| BOOT4-04.3 | Rollout opt-in, observação, rollback e autorização antes de provisionar | `aws-plan.md:73-82` ordena canário command-api, validação, expansão opcional a query-api, rollback e autorização própria antes de qualquer plano/aplicação remota. | PASS |

**Resumo de cobertura:** 17 PASS, 0 FAIL, 0 UNKNOWN. Critérios precisos foram associados a asserções ou evidência executável; os critérios documentais foram associados às declarações exatas da fonte de verdade.

## Gate de build e contagem

- **Comando:** `./mvnw.cmd --batch-mode -Dmaven.repo.local=C:\Users\vinic\.m2\repository -Pintegration clean verify`
- **Resultado:** exit 0, `BUILD SUCCESS`; 56 testes unitários + 69 integrações = 125, todos sem falha, erro ou skip.
- **Baseline anterior:** 54 unitários + 68 integrações = 122; delta +3, sem redução.
- **Observação:** houve mensagem de timeout do fork do Surefire após 30 s aguardando threads de shutdown depois de `System.exit(0)`. O Maven encerrou com código 0 e os reports Failsafe/Surefire contabilizam 125/125 sem falhas, erros ou skips. É uma ressalva de teardown/log, não reprovação do gate.

## Smokes Compose

| Verificação | Resultado |
| --- | --- |
| Três modos e contrato funcional | `docker compose -p flash-booking-boot4-smoke -f compose.yaml up -d --wait` terminou com query-api, command-api, worker, PostgreSQL, Valkey, LocalStack e Mailpit saudáveis. `scripts/compose-smoke.ps1` terminou exit 0: criou e leu evento, criou e leu reserva, conferiu a janela temporal e confirmou e-mail no Mailpit (`0988ed0b-5d40-47e8-9a6d-780a9d6d318b`). O teardown removeu somente containers e rede do projeto, sem volumes. |
| Emissão OTLP local | `scripts/observability-smoke.ps1` terminou exit 0: criou evento/reserva e encontrou HTTP trace e HTTP metrics no collector (`fb9963f9-b2b3-4f19-961b-3b2c6ac07e08`, `82830518-f6a3-4776-b4f0-af01f7d47972`). O `finally` do script removeu somente containers e rede efêmeros, sem volumes. |

## Sensor de discriminação

| Mutação em scratch | Resultado |
| --- | --- |
| Alterar `application.yml` para ligar `management.otlp.metrics.export.enabled` por padrão | **Morta.** `ObservabilityConfigurationTest.disablesRemoteTelemetryByDefault` falhou com “Expecting value to be false but was true” em `ObservabilityConfigurationTest.java:19`; exit 1. |

**Profundidade:** 1 mutação direcionada; 1/1 morta. Uma tentativa inicial alterou `management.defaults.metrics.export.enabled`, que não era a propriedade OTLP coberta; não foi contada como mutação válida.  
**Isolamento:** a mutação ocorreu em worktree temporário separado, removido após o teste. A árvore real permaneceu sem alterações antes da criação deste relatório.

## Checagens estruturais e qualidade

- `validate_spec.py .specs/features/spring-boot-4-observability/spec.md`: **0 errors, 0 warnings**.
- `validate_tasks.py .specs/features/spring-boot-4-observability/tasks.md`: **0 errors, 0 warnings**.
- `scripts/validate-readme.ps1`: **PASS**, 3 imagens no README, 26 referências locais e 3 cenários de desempenho.
- XML: SVG C4 high-load parseou como XML com raiz `<svg>`; texto alterado declara explicitamente que o alvo Boot 4.0.8 não foi aplicado.
- Qualidade: mudanças da feature respeitam o escopo aprovado; configurações permanecem opt-in e sem tags personalizadas de alta cardinalidade. A cobertura combina configuração, integração com collector, caminho degradado e contratos de API existentes. AWS não foi provisionada.

## Lacunas / próximos passos

- Nenhuma lacuna de critério encontrada nesta validação. Pixel-level QA do SVG não foi feito; como a mudança visual foi apenas uma linha de texto, foram conferidos conteúdo semântico, XML e validação documental.
- Traces/metrics AWS, IAM real, custo e observabilidade assíncrona não estão demonstrados por desenho: permanecem fora do escopo aplicado e sujeitos a autorização/fase separada.
