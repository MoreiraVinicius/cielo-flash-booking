# Especificação da arquitetura de alta carga

## Problem Statement

A demo funcional não deve receber toda a complexidade de produção antecipadamente. A evolução precisa absorver picos voláteis de consultas, reservas e trabalho assíncrono por mecanismos independentes, mantendo ausência de oversell, as mesmas regras de negócio e operação observável.

## Goals

O código herda a semântica de reserva e seus motivos do [ADR 0002](../../../docs/adr/0002-reserva-temporaria-com-motivo-de-encerramento.md), e o prazo de liberação de cinco segundos em operação saudável do [ADR 0003](../../../docs/adr/0003-prazo-de-liberacao-de-reservas-expiradas.md). A infraestrutura desta feature não será provisionada nem testada remotamente nesta entrega, conforme [ADR 0001](../../../docs/adr/0001-demo-publicada-alta-carga-sem-provisionamento.md); critérios operacionais abaixo são metas sem comprovação remota, a reconciliar nas tasks.

- [ ] Escalar leitura sem aumentar proporcionalmente a carga no banco principal.
- [ ] Escalar serviços de consultas, comandos e workers de forma independente antes e durante flash sales.
- [ ] Escalar a publicação da outbox sem multiplicar sistematicamente cada evento pelo número de workers.
- [ ] Remover pontos únicos de falha da demo.
- [ ] Definir gatilhos mensuráveis para evoluções posteriores.

## Out of Scope

| Item | Motivo |
| --- | --- |
| EKS | Só se justifica com plataforma Kubernetes dedicada. |
| Modelo de escrita DynamoDB | Só será considerado se a linha quente violar o SLO e uma nova ADR autorizar mudança de código. |
| Reserva assíncrona | Altera o contrato de produto. |
| Multi-region active-active | Complexidade sem requisito de RTO/RPO correspondente. |
| CI/CD | Não será avaliada. |

## Assumptions & Open Questions

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Pré-requisito | Demo com validação PASS | Evolução depende de baseline confiável. | yes |
| Banco | Aurora PostgreSQL Serverless | Escala por ACU e preserva JDBC e o modelo relacional; a promoção aplica ao schema compartilhado os campos operacionais exigidos pelo claim da outbox; ADR 0004. | yes |
| Cache | ElastiCache for Valkey Multi-AZ | Mantém o cache exclusivo de disponibilidade de eventos; só a topologia e capacidade mudam por Terraform; ADR 0005. | yes |
| Compute | ECS Fargate | Escala horizontal sem Kubernetes. | yes |
| Serviços | `query-api`, `command-api` e `worker` construídos da mesma base Java | Escala independente sem duplicar regras; adaptadores operacionais da evolução permanecem no mesmo repositório e artefato; ADR 0013. | yes |
| Segurança | API Gateway REST único, IAM/SigV4, WAF e throttling | Protege antes dos containers e preserva o mesmo contrato da demo; ADR 0012. | yes |
| Notificação | Mesmos eventos, filas, consumidores e SES da demo, com claim/lease no publisher compartilhado antes do scale-out | Consumidores continuam idempotentes, mas múltiplos publishers não devem reler simultaneamente o mesmo lote; ADR 0011. | yes |
| Gatilhos | SLO e percentual do envelope medido | TPS absoluto é volátil e específico do ambiente. | yes |

**Open questions:** none. SLOs, capacidade máxima e limites de custo são valores inicialmente provisórios, a substituir pelo baseline da demo antes de qualquer `apply`; a ausência de medição não autoriza promover a arquitetura.

## User Stories

### P1: Absorver picos de leitura

**História:** Como operador, quero que consultas de disponibilidade e reserva escalem sem obrigar o serviço de comandos a escalar junto.

**Critérios de aceite:**

1. QUANDO `GET /events/{id}` encontrar uma entrada válida no Valkey, ENTÃO o serviço de consultas DEVE responder sem consultar PostgreSQL.
2. SE Valkey estiver indisponível, ENTÃO `GET /events/{id}` DEVE consultar PostgreSQL dentro dos limites de timeout, circuito e fallback definidos no ADR 0005, sem consumir o pool reservado aos comandos.
3. ENQUANTO a disponibilidade for servida por cache, o sistema DEVE tratá-la como eventual e nunca usá-la para autorizar uma reserva.
4. QUANDO uma reserva, expiração ou cancelamento alterar capacidade, ENTÃO o sistema DEVE invalidar a chave de disponibilidade após o commit.
5. QUANDO `GET /reservations/{id}` for chamado, ENTÃO a Query API DEVE consultar o caminho read-write do PostgreSQL diretamente, sem depender do Valkey.
6. QUANDO a carga de GET crescer, ENTÃO somente o serviço `query-api` DEVE escalar pelas métricas de requisições, p95, CPU e hit rate de eventos.

**Teste independente futuro:** Aplicar pico de GET e medir cache hit, carga do writer e defasagem máxima. Nesta entrega, revisar o plano, as variáveis Terraform e testes com provider mockado; não executar carga remota.

### P1: Absorver picos de reserva

**História:** Como operador, quero aumentar a capacidade do serviço de comandos antes e durante flash sales sem violar inventário.

**Critérios de aceite:**

1. QUANDO uma flash sale programada se aproximar, ENTÃO a infraestrutura DEVE elevar antecipadamente somente a capacidade mínima do serviço de comandos.
2. ENQUANTO CPU, memória, p95 ou requisições por target excederem o alvo, o serviço de comandos DEVE aumentar tasks dentro do limite validado para o banco.
3. ENQUANTO múltiplas tasks reservarem o mesmo evento, o sistema DEVE manter disponibilidade entre zero e a capacidade total.
4. SE conexões crescerem com as tasks, ENTÃO o serviço de comandos DEVE usar o endpoint read-write do RDS Proxy para limitar churn no banco.
5. SE lock waits ou p99 ultrapassarem o SLO, ENTÃO o API Gateway DEVE aplicar controle de admissão antes de aumentar o máximo de tasks.

**Teste independente futuro:** Aplicar pico abrupto e verificar escala independente, latência e oversell zero. Nesta entrega, revisar o plano e validar Terraform sem aplicar recursos AWS.

### P1: Operar com alta disponibilidade

**História:** Como empresa, quero tolerar falhas de task e zona de disponibilidade.

**Critérios de aceite:**

1. ENQUANTO os serviços estiverem ativos, a infraestrutura DEVE manter ao menos duas tasks de consulta, duas de comando e duas de worker distribuídas entre zonas de disponibilidade.
2. SE uma task falhar, ENTÃO o ECS DEVE substituí-la sem intervenção manual.
3. SE o writer do banco falhar, ENTÃO o Aurora DEVE promover uma réplica em outra zona de disponibilidade.
4. SE uma mensagem falhar após as tentativas configuradas, ENTÃO o sistema DEVE preservá-la na DLQ específica e gerar alarme.

**Teste independente futuro:** Interromper componentes em ambiente controlado e observar recuperação. Nesta entrega, documentar o experimento e validar somente de forma estática/mockada.

### P1: Escalar a publicação da outbox

**História:** Como operador, quero executar múltiplos workers sem que cada publisher envie novamente o mesmo lote da outbox.

**Critérios de aceite:**

1. ENQUANTO houver mais de um publisher ativo, o sistema DEVE conceder no máximo um lease vigente por evento de outbox e DEVE excluir eventos com lease vigente dos demais lotes.
2. QUANDO um publisher adquirir um lote, o PostgreSQL DEVE selecionar e marcar no máximo o limite configurado em uma transação curta, com `FOR UPDATE SKIP LOCKED`, token de posse, lease baseado no relógio do banco e incremento de tentativa.
3. ENQUANTO o publisher chamar o SQS, o sistema DEVE manter encerrada a transação PostgreSQL usada para adquirir o lote.
4. QUANDO o SQS confirmar o envio, o sistema DEVE marcar o evento como publicado somente se o token de posse ainda corresponder ao claim vigente.
5. SE o publisher encerrar após o claim e antes da confirmação, ENTÃO o evento DEVE voltar a ser elegível após o lease expirar.
6. SE o resultado do envio ao SQS for ambíguo, ENTÃO o sistema DEVE aceitar possível redelivery e manter consumidores idempotentes, sem prometer entrega exatamente uma vez.

**Teste independente futuro:** Executar dois publishers sincronizados contra PostgreSQL e SQS, comprovar um envio por evento sem falha, interromper o dono do claim e comprovar recuperação após o lease; simular resposta ambígua e comprovar a segurança dos consumidores.

### P1: Evoluir por evidência

**História:** Como arquiteto, quero promover componentes somente quando métricas justificarem custo e complexidade.

**Critérios de aceite:**

1. QUANDO um teste de carga terminar, ENTÃO o relatório DEVE registrar TPS sustentável, p95, p99, erros, conexões, lock waits, hit rate e backlog separadamente para consultas, comandos e workers.
2. SE a previsão ou carga observada alcançar 60% do envelope sustentável, ENTÃO o plano operacional DEVE iniciar pré-escala ou revisão de capacidade do serviço afetado, sem escalar os demais automaticamente.
3. SE lock waits dominarem o p95 de reserva após tuning, ENTÃO a documentação DEVE exigir nova ADR antes de considerar DynamoDB síncrono ou SQS FIFO assíncrono.
4. O sistema DEVE registrar rollback para cada evolução habilitada.
5. QUANDO datas de abertura de venda ou proximidade do evento estiverem disponíveis, ENTÃO a observabilidade futura DEVE correlacioná-las com os padrões de consulta e comando; essa correlação não DEVE alterar regras de reserva automaticamente nesta entrega.

**Teste independente futuro:** Executar cenários separados de consulta, reserva e carga mista e aplicar a matriz de decisão. Nesta entrega, o documento define as métricas e o critério de promoção, sem alegar medições remotas.

### P1: Preservar o mesmo comportamento

**História:** Como responsável pelo sistema, quero trocar capacidade e topologia sem criar uma versão diferente das regras de negócio.

**Critérios de aceite:**

1. O ambiente de alta carga DEVE preservar controllers, módulos de domínio, eventos e contratos HTTP da demo em uma única imagem compartilhada pelos três modos; adaptações operacionais de persistência e suas migrations DEVEM integrar o schema compartilhado, sem criar um modelo de negócio paralelo.
2. SE uma alteração de capacidade exigir mudança nas regras de negócio Java, ENTÃO ela DEVE ser tratada como nova decisão de produto; adaptadores operacionais necessários para concorrência, roteamento ou leases pertencem à execução desta arquitetura.
3. QUANDO a topologia exigir endpoints read-only e read-write, ENTÃO a evolução DEVE implementar um adaptador explícito de roteamento sem alterar regras de negócio ou contratos HTTP.
4. A autenticação IAM, a notificação por e-mail e a ligação entre cliente, reserva e evento DEVEM permanecer idênticas nas duas arquiteturas.

**Teste independente futuro:** Verificar que uma única imagem atende os três modos, que eventos e reservas usam os endpoints definidos e que domínio, migrations e contratos permanecem compartilhados entre os ambientes.

## Edge Cases

- SE o cache servir valor desatualizado, ENTÃO a reserva DEVE continuar validando no writer.
- SE uma rajada ocorrer antes da pré-escala, ENTÃO o target tracking DEVE escalar o serviço afetado e o API Gateway DEVE aplicar controle de admissão.
- SE Valkey perder dados, ENTÃO `GET /events/{id}` DEVE reconstruir sua entrada a partir da fonte de verdade; `GET /reservations/{id}` DEVE permanecer independente do cache.
- SE uma zona de disponibilidade falhar, ENTÃO recursos distribuídos DEVEM continuar atendendo conforme o SLO.
- SE a réplica atrasar, ENTÃO a consulta de disponibilidade pode refletir consistência eventual, mas a consulta de reserva DEVE usar o caminho read-write.
- SE o SES falhar, ENTÃO o worker DEVE preservar a reserva e isolar a mensagem na fila de notificação.
- SE um publisher perder seu lease antes de confirmar a publicação, ENTÃO uma marcação tardia DEVE falhar e outro publisher DEVE poder adquirir o evento.

## Requirement Traceability

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| SCALE-01 | Picos de leitura | Design | Em design |
| SCALE-02 | Picos de reserva | Design | Em design |
| SCALE-03 | Alta disponibilidade | Design | Em design |
| SCALE-04 | Evolução por evidência | Design | Em design |
| SCALE-05 | Preservar o mesmo comportamento | Design | Em design |
| SCALE-06 | Escalar a publicação da outbox | Design | Em design |

**Cobertura:** 6 requisitos, 6 mapeados ao design, nenhum sem mapeamento.

## Success Criteria

- [ ] Picos de leitura não escalam a carga do writer na mesma proporção.
- [ ] Picos de reserva escalam somente o serviço de comandos, sem oversell.
- [ ] Picos de consulta escalam somente o serviço de consultas.
- [ ] Múltiplos publishers dividem eventos da outbox por claim/lease e recuperam claims abandonados sem manter transação aberta durante chamadas SQS.
- [ ] A evolução high-load preserva o mesmo domínio, contratos e regras de negócio, com adaptadores operacionais explícitos para sua topologia.
- [ ] Falha de task ou AZ possui recuperação documentada e validada estaticamente nesta entrega; o teste remoto fica explicitamente pendente.
- [ ] Cada evolução tem gatilho, custo, rollback e limite conhecido.
