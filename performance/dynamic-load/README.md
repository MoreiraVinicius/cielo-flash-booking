# Carga dinâmica local com dados fake

Este harness responde como a aplicação se comporta em uma stack Docker Compose local, com dados sintéticos determinísticos. Ele não mede capacidade de produção, não gera carga em AWS e não demonstra failover, autoscaling ou vazamento de heap JVM.

## Tipos de teste

| Tipo | Decisão | Motivo |
| --- | --- | --- |
| Smoke | Obrigatório | É o preflight de uma iteração, uma VU e quatro operações antes de qualquer perfil longo. |
| Stress | Obrigatório | Aumenta a taxa gradualmente para descobrir o primeiro limite local e o último degrau aprovado. |
| Spike | Obrigatório | Verifica a recuperação local após a onda 20 → 120 → 20 req/s. |
| Load | Condicional | Só pode executar com taxa e fonte de previsão de produção explicitamente aprovadas. |
| Capacity | Obrigatório após stress | Mede 30 minutos a 60% da taxa sustentável descoberta; não é a taxa esperada de produção. |
| Soak | P2 | Executar por duas horas a 60% da taxa sustentável somente após todos os gates curtos. |

## Pré-requisitos e guardrails

- Docker Desktop saudável, k6 e Git no `PATH`.
- O alvo é exclusivamente loopback local; o perfil não aceita URL remota.
- Smoke sempre é executado antes de um perfil longo. Não publique evidência canônica com worktree sujo.
- O runner exige ao menos duas instâncias `command-api-concurrency`, distribui o tráfego entre elas e sempre encerra o Compose.
- Os dados fake usam seed e `runId`, nomes/e-mails `example.com`, chaves idempotentes únicas e eventos hot/warm/cold 60/30/10. Nenhuma PII real é necessária.

## Executar

Cada comando grava artefatos descartáveis em `performance/dynamic-load/results/<runId>/`: resumo k6, telemetria JSONL, auditoria no `run.json`, resultado normalizado e relatório. `PASS` requer p95 de query abaixo de 500 ms, p95 de comando abaixo de 750 ms, falha técnica abaixo de 1% e auditoria de inventário válida.

```powershell
# Smoke: cerca de 1 minuto, exige somente Docker e k6.
.\performance\dynamic-load\run.ps1 -Profile smoke -Workload mixed

# Stress: warm-up de 30 s e degraus de 60 s; comece com taxas conservadoras.
.\performance\dynamic-load\run.ps1 -Profile stress -Workload mixed -StartRate 20 -StepRate 20 -MaxRate 200

# Spike: onda 20 → 120 → 20 req/s; confirmar recuperação em até 30 s.
.\performance\dynamic-load\run.ps1 -Profile spike -Workload mixed -SustainableRate 100

# Capacity: 30 min a 60% da taxa sustentável aprovada pelo stress.
.\performance\dynamic-load\run.ps1 -Profile capacity -Workload command-heavy -SustainableRate 100

# Load: somente com previsão de produção aprovada e sua fonte registrada.
.\performance\dynamic-load\run.ps1 -Profile load -Workload mixed -ForecastRate 80 -ForecastSource 'forecast-id-aprovado'

# Soak P2: duas horas; executar apenas após os perfis curtos aprovarem.
.\performance\dynamic-load\run.ps1 -Profile soak -Workload mixed -SustainableRate 100
```

Use `query-heavy`, `command-heavy` e `mixed` para separar leitura, escrita e mistura. Para publicar `result.json` e `report.md` como baseline, a árvore Git precisa estar limpa e todos os gates da matriz curta precisam ter passado:

```powershell
.\performance\dynamic-load\run.ps1 -Profile capacity -Workload mixed -SustainableRate 100 -PublishBaseline
```

## Telemetria e interpretação

Durante a execução local, o runner coleta CPU/memória dos containers, conexões e locks do PostgreSQL, métricas do Valkey e a auditoria de inventário (`available >= 0`, quantidades positivas e soma de reservas pendentes coerente). Evidência ausente, fixture incompleta, esgotamento acidental dos eventos ou carga limitada pelo gerador resulta em `INCOMPLETE`, `INVALID` ou `INCONCLUSIVE`, não em uma afirmação de capacidade.

CloudWatch é uma correlação somente leitura para uma execução AWS previamente autorizada: `cloudwatch.ps1` consulta janelas UTC de API Gateway, ECS, RDS, ElastiCache e SQS. Essa coleta não autoriza este runner a gerar tráfego remoto e não substitui os percentis do k6.

Um resultado local não prova capacidade de produção, alta carga em AWS, failover, autoscaling, distribuição regional ou vazamento de heap. O relatório sempre registra commit, ambiente, seed, duração e essas limitações.
