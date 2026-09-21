# Especificação de carga com dados fake dinâmicos

## Problem Statement

O baseline atual usa poucos dados previsíveis, dura 15 segundos e não encontra o limite sustentável da aplicação. A equipe precisa de uma suíte local reproduzível que gere eventos, clientes, chaves idempotentes e reservas dinâmicos, diferencie rejeição de negócio de falha técnica e produza evidência suficiente para decidir quando a arquitetura deve evoluir.

## Goals

- [ ] Executar smoke, stress e spike com massa fake determinística e sem dados pessoais reais.
- [ ] Medir o envelope sustentável local por consultas, comandos e tráfego misto.
- [ ] Validar um patamar operacional provisório de 60% do limite sustentável medido.
- [ ] Executar soak somente depois de os cenários curtos passarem.
- [ ] Preservar resultados brutos, metadados do ambiente e conclusões sem apresentá-los como capacidade de produção ou da arquitetura high-load.
- [ ] Correlacionar uma execução AWS explicitamente autorizada com as métricas CloudWatch já existentes, sem criar métricas de domínio nem disparar carga remota pelo harness.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Declarar carga esperada de produção | Não existe previsão de tráfego aprovada nem ambiente de produção ativo. O patamar de 60% é referência de capacidade local, não demanda de negócio. |
| Disparar carga na AWS pelo harness | A demo foi destruída e a arquitetura high-load permanece apenas documental conforme AD-006. Uma execução remota exige autorização, SigV4 e ferramenta de geração separada; o harness somente lê sua telemetria depois. |
| Provisionar ou alterar a arquitetura high-load | Esta feature mede a demo local; mudanças de topologia dependem dos gargalos encontrados e de autorização futura. |
| Alterar schema, índices ou código de produção | O primeiro ciclo cria o instrumento de medição. Otimizações exigem evidência e feature própria. |
| Teste destrutivo, DDoS ou bypass de IAM/WAF | Os cenários têm limites locais explícitos e não atacam endpoints públicos. |
| Provar ausência de oversell somente com k6 | A prova primária continua nos testes concorrentes de integração; a suíte de carga apenas audita invariantes após a execução local. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Ambiente inicial | Docker Compose local, efêmero e isolado | Evita custo e respeita AD-006; resultados ficam rotulados como locais. | no, default do plano |
| Ferramenta | k6 0.48+ e PowerShell, reaproveitando `performance/demo/` | Já fazem parte do baseline e reduzem novas dependências. | no, default do plano |
| Dados fake | Gerador próprio, determinístico por seed, sem biblioteca Faker | Nome, e-mail, UUID lógico e chave idempotente exigem pouca variedade; uma dependência externa não melhora a validade da carga. | no, default do plano |
| Seed canônica | `20260921` | Permite reproduzir a seleção dos dados; cada execução ainda recebe `runId` único para evitar colisão persistida. | no, default do plano |
| Distribuição de eventos | 60% hot, 30% warm e 10% cold sobre 12 eventos | Exercita hot row e dispersão sem misturar os resultados em um único evento. | no, default do plano |
| Mix misto | 70% consulta de evento, 20% criação de reserva, 5% consulta de reserva e 5% cancelamento | Representa uma flash sale predominantemente de leitura e cobre o ciclo de reserva. | no, default do plano |
| Limite sustentável | Maior degrau de 60 s que conclui com falhas técnicas abaixo de 1%, p95 de GET abaixo de 500 ms, p95 de comandos abaixo de 750 ms e nenhuma violação de invariante | Reaproveita os thresholds do baseline e exige estabilidade durante um degrau completo. | no, default do plano |
| Patamar operacional provisório | 60% da taxa sustentável descoberta, por workload | Mantém folga para variação local; não substitui uma previsão de produção. | no, default do plano |
| Soak | 2 horas no patamar provisório, iniciado apenas após smoke, stress, spike e validação de 30 minutos passarem | Cobre ciclos de expiração de 10 minutos e revela tendências de memória, conexões e latência sem tornar o primeiro feedback excessivamente lento. | no, default do plano |
| Publicação de baseline | Exige worktree limpa; execuções descartáveis podem ocorrer em worktree suja | Mantém proveniência sem bloquear experimentação local. | no, default do plano |
| CloudWatch remoto | Consulta somente leitura com janela UTC iniciando 5 minutos antes e terminando 10 minutos depois da carga autorizada | API Gateway e ECS publicam métricas em períodos de um minuto; a margem absorve publicação tardia sem atribuir pontos externos ao run. | no, default do plano |

**Open questions:** none - all unresolved choices use the defaults above and remain subject to user approval before Execute.

---

## User Stories

### P1: Gerar carga reproduzível com dados dinâmicos ⭐ MVP

**User Story**: Como engenheiro, quero gerar dados fake variados e determinísticos para que a carga não dependa de fixtures fixas nem de dados reais.

**Why P1**: Todos os cenários dependem de massa válida, identificável e reproduzível.

**Acceptance Criteria**:

1. WHEN uma execução iniciar THEN a suíte SHALL criar 12 eventos e clientes sintéticos usando `seed`, `runId`, VU e iteração para produzir valores válidos dentro dos limites da API.
2. WHEN a mesma seed e os mesmos identificadores forem usados THEN o gerador SHALL produzir a mesma sequência de nomes, e-mails, quantidades e seleção de eventos.
3. WHEN uma nova operação mutável for emitida THEN a suíte SHALL enviar uma `Idempotency-Key` única de no máximo 128 caracteres.
4. The suite SHALL use only reserved `example.com` e-mail addresses and SHALL not read production or personal datasets.
5. WHEN o workload misto selecionar eventos THEN a suíte SHALL direcionar 60% das seleções ao grupo hot, 30% ao grupo warm e 10% ao grupo cold, com tolerância de 2 pontos percentuais em 10.000 seleções determinísticas.

**Independent Test**: Executar o contrato do gerador duas vezes com a mesma seed e comparar o hash da sequência, os limites dos campos e a distribuição de 10.000 seleções.

---

### P1: Validar o harness com smoke test

**User Story**: Como engenheiro, quero uma verificação curta antes da carga para que erro de script, rota, saúde ou massa falhe cedo.

**Why P1**: Nenhum teste longo deve começar com o sistema ou o harness quebrado.

**Acceptance Criteria**:

1. WHEN o perfil `smoke` for executado THEN a suíte SHALL usar 1 VU e concluir um ciclo de criar evento, consultar evento, criar reserva, consultar reserva e cancelar reserva em até 60 segundos.
2. IF qualquer health check, status HTTP esperado, parse de resposta ou invariante do ciclo falhar THEN o runner SHALL encerrar antes dos perfis de carga e retornar código diferente de zero.
3. WHEN o smoke terminar THEN o runner SHALL registrar zero falhas técnicas, as contagens por rota e a proveniência da execução.

**Independent Test**: Executar `smoke` contra a stack Compose recém-criada e confirmar o ciclo completo e o encerramento limpo.

---

### P1: Encontrar o limite sustentável com stress test

**User Story**: Como engenheiro, quero aumentar a taxa progressivamente para descobrir o limite local e o primeiro recurso saturado.

**Why P1**: A arquitetura high-load deve evoluir por gargalo medido, não por estimativa.

**Acceptance Criteria**:

1. WHEN o perfil `stress` for executado THEN a suíte SHALL medir workloads query-heavy, command-heavy e mixed em degraus de 60 segundos, com aquecimento de 30 segundos e incremento configurável de taxa.
2. WHEN um degrau completo satisfizer falhas técnicas abaixo de 1%, p95 de GET abaixo de 500 ms e p95 de comandos abaixo de 750 ms THEN a suíte SHALL classificá-lo como sustentável.
3. WHEN o primeiro degrau violar qualquer threshold por 60 segundos THEN a suíte SHALL interromper a progressão, preservar a evidência e registrar o degrau anterior como limite sustentável.
4. WHEN o stress terminar THEN a suíte SHALL identificar como primeiro gargalo ao menos um sinal observado entre CPU, memória, conexões PostgreSQL, lock waits, hit rate Valkey ou capacidade do gerador; se nenhum saturar, SHALL registrar `inconclusivo`.

**Independent Test**: Rodar os três workloads até uma violação ou o teto seguro configurado e obter um limite por workload sem alegar capacidade de produção.

---

### P1: Medir rajada com spike test

**User Story**: Como engenheiro, quero aplicar um salto abrupto de tráfego para medir a degradação e a recuperação de uma flash sale.

**Why P1**: Rajadas são centrais ao domínio e não são representadas por rampas graduais.

**Acceptance Criteria**:

1. WHEN o perfil `spike` for executado THEN a suíte SHALL manter 30 segundos a 20% do limite sustentável, saltar por 60 segundos para 120% e retornar por 60 segundos a 20%.
2. WHEN a rajada terminar THEN a suíte SHALL registrar falhas técnicas, rejeições de negócio, p95 e p99 por rota separadamente em cada fase.
3. WHEN a taxa retornar a 20% THEN a suíte SHALL recuperar os thresholds de latência e falha em até 30 segundos ou SHALL marcar a recuperação como falha.

**Independent Test**: Executar a forma de onda contra o workload misto e comprovar se a aplicação retorna ao patamar anterior.

---

### P1: Validar o patamar operacional provisório

**User Story**: Como engenheiro, quero validar uma carga estável abaixo do limite para distinguir pico suportado de operação sustentável.

**Why P1**: Stress encontra o limite; uma janela constante confirma a margem operacional local.

**Acceptance Criteria**:

1. WHEN existir limite sustentável para um workload THEN o perfil `capacity` SHALL executar por 30 minutos a 60% desse limite.
2. WHILE o perfil `capacity` estiver ativo the suite SHALL manter falhas técnicas abaixo de 1%, p95 de GET abaixo de 500 ms e p95 de comandos abaixo de 750 ms em cada janela de 1 minuto.
3. The report SHALL label this result as `capacidade local provisória` and SHALL not call it `carga esperada de produção`.
4. WHERE uma previsão aprovada de produção estiver configurada the suite SHALL execute that arrival rate as a distinct `load` profile and SHALL preserve the forecast source in the evidence.

**Independent Test**: Derivar 60% do stress e executar a janela de 30 minutos com thresholds por minuto.

---

### P2: Detectar degradação prolongada com soak test

**User Story**: Como engenheiro, quero observar a aplicação por duas horas para detectar tendências que os cenários curtos não revelam.

**Why P2**: É valioso, mas só depois de corrigir falhas rápidas e custa mais tempo de execução.

**Acceptance Criteria**:

1. WHEN smoke, stress, spike e capacity estiverem aprovados THEN o perfil `soak` SHALL executar o workload misto por 2 horas a 60% do limite sustentável misto.
2. WHILE o soak estiver ativo the suite SHALL capturar a cada 10 segundos RSS/CPU dos containers e, a cada 5 segundos, conexões e lock waits do PostgreSQL e hits/misses do Valkey.
3. WHEN o soak terminar THEN o relatório SHALL comparar os primeiros e os últimos 15 minutos e falhar se p95 crescer mais de 20%, falhas técnicas atingirem 1% ou mais, a mediana de conexões aumentar em seis janelas consecutivas de 5 minutos e terminar mais de 20% acima da primeira, ou a mediana de RSS dos últimos 15 minutos exceder em mais de 25% a mediana dos primeiros 15 minutos após aquecimento.
4. The report SHALL state that container RSS is a leak indicator and SHALL not claim direct JVM heap-leak proof.

**Independent Test**: Executar duas horas após os gates curtos e obter comparação temporal com verdict binário.

---

### P1: Produzir evidência segura e auditável

**User Story**: Como responsável técnico, quero resultados comparáveis e guardrails para que a carga não seja confundida com evidência de produção nem executada no alvo errado.

**Why P1**: Sem proveniência, separação de erros e limites de destino, os números são enganosos e a execução pode ser perigosa.

**Acceptance Criteria**:

1. The runner SHALL accept only loopback targets by default and SHALL reject non-loopback URLs without an explicit remote opt-in unavailable in this version.
2. WHEN uma execução terminar or fail THEN o runner SHALL stop the ephemeral Compose stack and preserve the partial evidence.
3. WHEN resultados forem gravados THEN a evidência SHALL incluir commit, dirty flag, seed, runId, profile, workload, rates, durations, versions, hardware, container count, thresholds and timestamps.
4. WHEN respostas HTTP forem classificadas THEN a suíte SHALL separate technical failures from expected business rejections such as sold-out, closed sale or idempotency conflict.
5. WHEN o audit local pós-execução rodar THEN ele SHALL confirmar `event.available >= 0`, nenhuma reserva com quantidade não positiva e `event.capacity - event.available = sum(reservation.quantity WHERE reservation.status = 'PENDING')` por evento.
6. WHERE uma execução AWS tiver sido autorizada e seus instantes UTC forem fornecidos THEN o coletor SHALL ler do CloudWatch, sem enviar requisições à API, `Count`, `4XXError`, `5XXError`, `Latency` e `IntegrationLatency` por rota do API Gateway, CPU/memória por serviço ECS, CPU e `DatabaseConnections` do RDS, sinais de Valkey e backlog/idade/DLQ do SQS.
7. IF uma série CloudWatch estiver ausente, atrasada ou sem a dimensão esperada THEN o relatório SHALL marcar a correlação como incompleta e SHALL not infer a health do componente ausente.

**Independent Test**: Forçar uma falha de threshold e confirmar destino bloqueado, limpeza, artefato parcial e categorias separadas.

---

## Edge Cases

- IF o gerador de carga saturar antes do sistema THEN o relatório SHALL marcar o limite como `generator-bound` e não como limite da aplicação.
- IF o estoque planejado for insuficiente para o cenário THEN a suíte SHALL classificar `sold-out` como rejeição de negócio e SHALL invalidate a medição de capacidade de comandos.
- IF a stack não expuser ao menos duas instâncias `command-api` THEN os perfis de stress, spike, capacity e soak SHALL abort before load generation.
- IF uma consulta de telemetria PostgreSQL ou Valkey falhar THEN o workload SHALL finish safely and the report SHALL mark observability evidence as incomplete.
- IF uma consulta CloudWatch retornar dados fora da janela ou sem pontos THEN o relatório SHALL preservar a resposta e marcar a correlação remota como incompleta.
- IF o working tree estiver sujo THEN o runner SHALL allow disposable runs and SHALL reject canonical result publication.
- WHEN nomes ou e-mails fake atingirem seus limites THEN o gerador SHALL keep names at 200 characters or fewer and e-mails at 320 characters or fewer.
- WHEN o mesmo cliente sintético reaparecer THEN o payload SHALL preserve the normalized lower-case e-mail and a stable name.

---

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation & bounds | Perfis, taxas, durações, seed, URLs, nomes, e-mails e chaves possuem limites validados antes da carga. |
| Failure / partial-failure states | Evidência parcial é preservada; a stack é encerrada; telemetria incompleta recebe rótulo explícito. |
| Idempotency / retry / duplicate handling | Cada mutação recebe chave única e um subcenário controlado verifica replay sem misturá-lo à vazão normal. |
| Auth boundaries & rate limits | V1 aceita apenas loopback; IAM/SigV4, WAF e carga remota ficam fora do escopo. |
| Concurrency / ordering | Ao menos duas command-api recebem tráfego; resultados e auditoria distinguem hot row, warm e cold. |
| Data lifecycle / expiry | A stack efêmera é destruída; o soak cobre múltiplos ciclos de expiração de reserva. |
| Observability | k6, Docker, PostgreSQL e Valkey fornecem séries locais; em execução AWS autorizada, CloudWatch correlaciona borda, ECS, RDS, Valkey e SQS em uma janela UTC definida. |
| External-dependency failure | N/A because the local suite does not inject dependency failures; resilience remains in the high-load plan. |
| State-transition integrity | Smoke percorre criação, leitura e cancelamento; audit pós-execução verifica invariantes persistidas. |

---

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| LOAD-01 | Dados fake dinâmicos e reproduzíveis | Execute | Implementing |
| LOAD-02 | Smoke test | Execute | Implementing |
| LOAD-03 | Stress test | Execute | Implementing |
| LOAD-04 | Spike test | Execute | Implementing |
| LOAD-05 | Capacity e load condicionado | Execute | Implementing |
| LOAD-06 | Soak test | Execute | Implementing |
| LOAD-07 | Evidência e observabilidade | Design | In Design |
| LOAD-08 | Guardrails e auditoria de invariantes | Design | In Design |
| LOAD-09 | Correlação CloudWatch somente leitura | Design | In Design |

**Coverage:** 9 total, 9 mapped to design, 0 unmapped.

---

## Success Criteria

- [ ] Smoke falha cedo ou conclui o ciclo completo em até 60 segundos.
- [ ] Stress produz um limite sustentável ou um resultado `inconclusivo` fundamentado para cada workload.
- [ ] Spike mede degradação e recuperação sem confundir rejeição de negócio com erro técnico.
- [ ] Capacity sustenta 30 minutos a 60% do limite medido.
- [ ] Soak, quando promovido, produz verdict sobre degradação de duas horas e declara o limite da medição de RSS.
- [ ] Toda evidência publicada é reproduzível, sanitizada e rotulada como local ou como correlação AWS autorizada, sem inferir capacidade de produção.
