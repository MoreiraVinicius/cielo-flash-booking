# Contexto da carga com dados fake dinâmicos

**Gathered:** 2026-09-21
**Spec:** `.specs/features/dynamic-fake-load/spec.md`
**Status:** Ready for design

---

## Feature Boundary

Esta feature cria uma suíte local de carga reproduzível para a demo existente. Ela gera sua própria massa fake, mede capacidade e rajadas, observa tendências prolongadas e publica evidência local. Ela não provisiona AWS, não implementa a arquitetura high-load e não altera o comportamento da aplicação.

---

## Implementation Decisions

### Seleção dos tipos de teste

- Smoke é obrigatório em toda execução e funciona como preflight.
- Stress é obrigatório no primeiro ciclo porque o projeto ainda não conhece o limite sustentável atual.
- Spike é obrigatório porque o produto modela flash sales e precisa medir recuperação após rajada.
- Load no sentido estrito de “carga esperada de produção” não será alegado sem previsão aprovada. A suíte executará `capacity` a 60% do limite local e manterá um perfil `load` condicionado a uma fonte de forecast.
- Soak é P2 e só roda depois que smoke, stress, spike e capacity passam. O default é duas horas.

### Dados fake

- O gerador é determinístico por seed e não depende de uma biblioteca Faker.
- Os e-mails usam somente `example.com`; nenhum dataset externo ou pessoal entra na suíte.
- `runId`, VU, iteração e operação formam chaves idempotentes únicas e auditáveis.
- Doze eventos formam grupos hot, warm e cold. A capacidade é calculada antes de cada perfil para evitar sold-out acidental.
- Sold-out intencional, replay idempotente e conflito de chave são subcenários funcionais separados; não poluem a medição normal de vazão.

### Segurança operacional

- V1 aceita apenas URLs loopback e sobe uma stack Compose efêmera.
- Carga remota, IAM/SigV4 e AWS exigem uma feature e autorização futuras.
- Resultados canônicos só podem ser publicados a partir de uma worktree limpa.
- O runner sempre encerra a stack e preserva resultados parciais.

### Evidência

- Latência e falha são separadas por rota e fase do perfil.
- Respostas esperadas de domínio não contam como falhas técnicas, mas aparecem em contadores próprios.
- CPU e RSS dos containers, conexões e waits PostgreSQL e hits/misses Valkey formam séries temporais.
- A auditoria PostgreSQL é somente leitura, roda depois da carga e não exige schema ou índice novo.
- Para uma carga AWS autorizada por fora do harness, o coletor consulta CloudWatch em modo somente leitura. Ele correlaciona API Gateway, ECS, RDS, Valkey e SQS com os instantes UTC informados pelo operador.
- CloudWatch não substitui o resumo k6: ele explica onde a plataforma saturou; k6 permanece a fonte de p95/p99 e de classificação de resposta por requisição.

### Agent's Discretion

- Organização interna dos módulos JavaScript do k6.
- Formato interno dos arquivos brutos, desde que o resumo canônico seja JSON versionado e o relatório humano seja Markdown.
- Incremento e teto locais do stress, desde que sejam configuráveis, registrados e respeitem os guardrails da spec.

---

## Deferred Ideas

- Assinatura SigV4 e geração remota de carga na demo AWS.
- Execução distribuída do gerador.
- Telemetria direta de heap JVM por endpoint Actuator protegido.
- Failure injection de Valkey, PostgreSQL, filas e workers, já prevista na arquitetura high-load.
