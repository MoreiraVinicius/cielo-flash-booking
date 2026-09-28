# Spring Boot 4 e observabilidade — especificação

## Problem Statement

A aplicação usa Spring Boot 4.0.8, Java 21 e Jackson 3. A migração preserva o contrato de reservas temporárias, idempotência, expiração, outbox e autenticação IAM/SigV4 da borda. Métricas e traces HTTP OTLP são opt-in localmente; a integração AWS permanece um plano não aplicado.

## Goals

- [x] Executar a migração para a linha estável 4.0.x com testes de regressão do domínio e dos três modos de processo.
- [x] Oferecer métricas e traces HTTP via OpenTelemetry, com exportação desabilitada por padrão e habilitação explícita no ambiente local.
- [x] Atualizar especificações, documentos e diagramas afetados, distinguindo comportamento implementado, plano AWS e evidência histórica.
- [x] Definir um plano AWS com destino, permissões, custo, segurança e critérios de rollout, sem provisionamento nesta mudança.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Deploy, `terraform apply` ou alteração em conta AWS | A autorização recebida cobre planejamento AWS, não implantação. |
| Implementar topologia high-load ou alegar validação remota dela | AD-006 mantém esse desenho como evolução não aplicada. |
| Criar métricas de negócio de reservas únicas, vendas ou receita | Não são equivalentes às interações HTTP do AD-024 e requerem contrato próprio. |
| Rastrear continuamente cada mensagem através de outbox, SQS e worker | Exige persistir e propagar contexto, além do starter; decisão ainda não confirmada. |
| Mudar schema PostgreSQL, regras de reserva, formatos JSON ou autenticação de clientes | A migração de framework não autoriza mudanças de produto. |
| Ativar exportação OTLP de logs | Os logs atuais continuam em stdout/CloudWatch; o starter não implica exportador de logs. |

## Assumptions & Open Questions

| Tema | Padrão escolhido | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Versão | Runtime usa Boot 4.0.8 e Java 21, após passagem por Boot 3.5.16 | A sequência do guia oficial foi aplicada e a suíte existente validou o runtime atual. | yes — implementada |
| Jackson | Usar Jackson 3 nativo | Imports e API foram migrados e a suíte rápida e de integração manteve os contratos JSON. | yes — implementada |
| Telemetria padrão | Exportação OTLP desabilitada até ativação explícita | Evita custo, tráfego não intencional e mudança na demo AWS existente. | yes — testes de configuração |
| Traces assíncronos | Nesta entrega, provar HTTP; SQS/outbox fica para uma etapa separada | Um starter não transporta automaticamente contexto persistido entre transação e fila. | no — resposta solicitada ao usuário |
| Integração AWS | Planejar CloudWatch/X-Ray via coletor/agent ECS, com escolha final antes da implantação | Mantém a aplicação emitindo OTLP e explicita IAM, custo e rollout. | yes — planejamento solicitado, implantação não |
| Build Docker da smoke | Usar o JAR já compilado no host no overlay descartável | O Maven dentro do build Docker ficou sem progresso resolvendo dependências; o JAR host foi validado e a smoke Compose comprovou emissão OTLP. O Dockerfile normaliza CRLF do wrapper quando a imagem de produção for construída. | yes — smoke executada |

**Open questions:** none. A rastreabilidade contínua outbox → SQS → worker está fora desta entrega por padrão, como registrado acima, salvo decisão explícita do usuário antes da implementação.

## User Stories

### P1: Migrar o runtime sem regressão

**User Story:** Como mantenedor, quero executar o mesmo domínio em Spring Boot 4 para estudar a versão nova sem perder as garantias do case.

**Acceptance Criteria:**

1. WHEN o projeto é compilado para a entrega THEN o parent Maven SHALL ser Spring Boot 4.0.8 e o bytecode SHALL permanecer configurado para Java 21.
2. WHEN os testes rápidos e de integração são executados em ambiente com dependências e Docker disponíveis THEN o projeto SHALL passar sem desabilitar, excluir ou enfraquecer testes existentes.
3. WHEN a aplicação inicia em cada modo `query-api`, `command-api` e `worker` THEN cada processo SHALL criar somente seus componentes previstos e atingir o estado saudável existente.
4. WHEN as cinco operações HTTP do case são exercitadas THEN a aplicação SHALL preservar os códigos de status, headers e payloads JSON especificados em `flash-booking-demo/spec.md`.
5. WHEN comandos repetidos, concorrentes ou expirados são exercitados contra PostgreSQL real THEN a aplicação SHALL preservar as invariantes de não oversell, idempotência de 24 horas, outbox transacional e expiração definidas nas specs atuais.

**Independent Test:** Executar a suíte Maven rápida, a suíte de integração e o smoke Compose dos três modos; comparar as respostas dos testes de contrato existentes.

### P1: Observar HTTP localmente sem mudar o domínio

**User Story:** Como apresentador, quero mostrar métricas e traces HTTP do Boot 4 sem depender de uma conta AWS.

**Acceptance Criteria:**

1. WHILE a observabilidade OTLP não estiver habilitada THEN a aplicação SHALL não enviar métricas, traces ou logs por OTLP.
2. WHERE o perfil local de observabilidade estiver habilitado THEN a aplicação SHALL exportar métricas HTTP e traces HTTP para um endpoint OTLP local explicitamente configurado.
3. WHEN uma chamada HTTP instrumentada contém o header `X-Correlation-ID` THEN os logs de aplicação SHALL continuar exibindo esse identificador no formato documentado.
4. IF o receptor OTLP estiver indisponível THEN a aplicação SHALL continuar aceitando e concluindo as operações de reserva conforme seus contratos HTTP, sem transformar falha de exportação em erro de negócio.
5. WHILE apenas o starter de observabilidade estiver ativo THEN a documentação SHALL não afirmar propagação contínua por outbox/SQS/worker nem exportação OTLP de logs.

**Independent Test:** Subir o receptor local, executar uma chamada HTTP, observar métrica e trace; parar o receptor e repetir a chamada com o mesmo resultado de negócio.

### P1: Alinhar fontes de verdade e representações visuais

**User Story:** Como avaliador, quero distinguir o que está implementado do que é uma integração futura ao ler specs, README e diagramas.

**Acceptance Criteria:**

1. WHEN a migração é validada THEN `.specs/STATE.md` SHALL substituir a decisão ativa de Boot 3 por uma decisão de Boot 4, mantendo o histórico da anterior como superseded.
2. WHEN documentação e diagramas são revisados THEN referências ao runtime atual SHALL indicar Spring Boot 4, e imagens históricas SHALL continuar rotuladas como evidência histórica quando aplicável.
3. WHEN a documentação descreve observabilidade AWS THEN ela SHALL identificar explicitamente o plano como não implantado e manter os dashboards atuais do AD-023/AD-024 como evidência da demo existente.
4. WHEN o validador documental é executado THEN links para assets SHALL resolver e SVGs alterados SHALL ser XML válido.

**Independent Test:** Buscar referências Boot 3/4 no repositório, executar validadores de documentação e inspeção visual dos SVGs/PNGs afetados.

### P2: Preparar a integração AWS sem aplicá-la

**User Story:** Como operador, quero um caminho seguro e reversível para enviar telemetria dos serviços ECS à AWS após a migração local.

**Acceptance Criteria:**

1. WHEN o plano AWS é apresentado THEN `.specs` SHALL descrever origem OTLP, coletor/agent na task ECS, destino de métricas e traces, e relação com os dashboards existentes.
2. WHEN o plano AWS é apresentado THEN `.specs` SHALL descrever permissões IAM mínimas, limites de cardinalidade, amostragem, retenção e possíveis custos.
3. WHEN o rollout AWS é planejado THEN `.specs` SHALL definir habilitação opt-in por serviço, observação, rollback e critérios que exigem autorização antes de provisionar.

**Independent Test:** Revisar o plano contra as task definitions Terraform atuais e as guias oficiais de Spring/AWS, sem executar `terraform apply`.

## Edge Cases

- IF a migração trocar serialização de datas, enums ou erros THEN a suíte de contratos SHALL falhar antes da entrega.
- IF a exportação OTLP introduzir tags com `customerId`, e-mail, chave idempotente ou `reservationId` THEN a validação de configuração SHALL rejeitar esse desenho por cardinalidade e privacidade.
- IF Maven ou Docker permanecerem inacessíveis THEN a validação SHALL registrar o gate não executado e a entrega SHALL permanecer não verificada.
- IF uma imagem representar a demo AWS antiga THEN a revisão SHALL preservar o rótulo histórico em vez de reapresentá-la como Boot 4 implantado.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| BOOT4-01 | P1: Migrar o runtime sem regressão | Execute | Concluído; verificação independente PASS |
| BOOT4-02 | P1: Observar HTTP localmente sem mudar o domínio | Execute | Concluído; verificação independente PASS |
| BOOT4-03 | P1: Alinhar fontes de verdade e representações visuais | Execute | Concluído; verificação independente PASS |
| BOOT4-04 | P2: Preparar a integração AWS sem aplicá-la | Execute | Concluído como plano; verificação independente PASS; AWS não aplicada |

**Coverage:** 4 requisitos, 17 critérios de aceitação; BOOT4-01–04 rastreados às tarefas T1–T15. Validação independente em `validation.md`: 17 PASS, 0 FAIL, 0 UNKNOWN.

## Success Criteria

- [x] Boot 4.0.8 compila, testes rápidos e integração passam, e os três processos iniciam.
- [x] O receptor local demonstra métricas e traces HTTP, e sua falha não muda o resultado de reserva.
- [x] Documentação, specs e diagramas indicam corretamente versão atual, estado de implantação e limites da telemetria.
- [x] Plano AWS completo está registrado, sem alteração remota.

**Feature status:** Complete — verificação independente PASS em 2026-09-28. Plano AWS permanece não aplicado.

