# Avaliação da entrega contra o case

Esta matriz separa três níveis que não devem ser confundidos:

- **Implementado**: existe código ou infraestrutura versionada.
- **Validado**: há teste, gate ou observação remota citada.
- **Arquitetura-alvo**: existe design verificável, mas ainda não há runtime provisionado.

A fonte consolidada da demo é a [validação independente](../.specs/features/flash-booking-demo/validation.md), concluída em 2026-09-11. A arquitetura [high-load](../.specs/features/flash-booking-high-load/design.md) é uma evolução planejada e não foi aplicada nem testada remotamente.

## Resultado em uma página

| Área | Demo entregue | High-load alvo |
| --- | --- | --- |
| Estado | **Baseline PASS — 43/43 critérios**; versão atual inclui janela de flash sale e passou no gate PostgreSQL 16/Testcontainers | **Planejada — sem provisionamento remoto** |
| Código Java | Implementado e compartilhado entre três modos | Preserva domínio, contratos e schema; adaptadores operacionais pertencem à evolução futura |
| Execução local | Compose e smoke test validados | Não é um segundo produto local |
| AWS | Plan/apply, probes e destroy concluídos; state final vazio | Topologia Multi-AZ descrita, não aplicada |
| Testes | Baseline: 38 unitários + 56 de integração = **94 aprovados**. Atual: **53 unitários + 68 integrações = 121 aprovados** no gate PostgreSQL 16/Testcontainers. | Reutiliza gates da demo; falha/capacidade remotas pendentes |
| Desempenho | Baseline local curto; não representa capacidade de produção | Sem benchmark ou SLO comprovado |

## Requisitos funcionais

| Rota do case | Comportamento entregue | Evidência principal |
| --- | --- | --- |
| `POST /events` | Cria evento com capacidade positiva e `startsAt`/`endsAt` opcionais; erros usam Problem Details e idempotência obrigatória. | `EventControllerIT` e `IdempotencyControllerIT` |
| `GET /events/{id}` | Retorna capacidade, janela configurada e disponibilidade com cache-aside de até 1 segundo. | `EventControllerIT` |
| `POST /events/{id}/reservations` | Persiste cliente e reserva `PENDING`, decrementa estoque e grava outbox na mesma transação, somente durante a janela comercial. | `ReservationControllerIT` e testes de concorrência |
| `GET /reservations/{id}` | Retorna a reserva, o cliente e a referência estável do evento `{id, name}` diretamente do PostgreSQL. | `ReservationQueryControllerIT` |
| `DELETE /reservations/{id}` | Antes do prazo encerra como CANCELLED; no prazo ou depois materializa EXPIRED; devolve estoque exatamente uma vez. | `ReservationQueryControllerIT` e testes de corrida/lock |

O domínio entregue é de **reserva temporária**. Não há pagamento, compra confirmada ou emissão de ingresso. O e-mail informa a reserva e seu prazo, sem prometer venda concluída.

## Requisitos não funcionais

| Requisito | Mecanismo | Evidência e limite |
| --- | --- | --- |
| Oversell zero | Decremento condicional no PostgreSQL e constraints; criação da reserva ocorre na mesma transação. | Testes concorrentes aceitam no máximo a capacidade. O benchmark não é a prova dessa propriedade. |
| Janela de flash sale | `startsAt`/`endsAt` opcionais no evento; PostgreSQL combina relógio, capacidade e janela no mesmo decremento. | Início é inclusivo, fim é exclusivo; antes/depois retorna `409` sem efeito parcial. |
| Múltiplas instâncias | `query-api`, `command-api` e `worker` iniciam a mesma imagem em modos separados; perfil local cria duas réplicas adicionais de comandos. | Configuração e harness local; a demo AWS econômica usou uma task por serviço. |
| Expiração automática | Outbox, SQS com atraso, consumidor idempotente e reconciliador pelo relógio do banco. | Integração e probe remoto; devolução saudável até `expiresAt + 5s`. |
| Idempotência | Resultado final persistido no PostgreSQL por uma janela de 24 horas, ligado a operação, alvo e hash do payload; aquisição e vencimento usam o relógio do banco. | Dentro da janela, repetição igual devolve a mesma resposta e conflito recebe `409`; após ela, a chave pode ser reivindicada atomicamente. O worker limpa somente vencidos em lotes. |
| Consistência eventual | Cache-aside Valkey somente para disponibilidade de evento, TTL máximo de 1 segundo e invalidação após commit. | Testes de hit, miss, invalidação e fallback; consulta de reserva não depende do cache e cache nunca autoriza comando. |
| Erros explícitos | `application/problem+json`, correlation ID e códigos `400`, `403`, `404`, `409`, `500` e `503`. | Testes de controller e falhas de dependência. |

## Resiliência e observabilidade

| Risco | Padrão aplicado | Sinal observado |
| --- | --- | --- |
| Mensagem perdida ou repetida | Transactional outbox, entrega pelo menos uma vez, consumidor idempotente e DLQ por fluxo | backlog, idade da mensagem, DLQ e logs correlacionados |
| Cache de evento indisponível | timeout de 100 ms, bulkhead de 5 fallbacks por task e circuit breaker após 5 falhas/10 s | hit rate, falhas, circuito aberto e pressão no banco |
| Expiração atrasada | mensagem atrasada + reconciliador consultando o relógio do PostgreSQL | idade da fila e atraso até o estado terminal |
| Corrida entre cancelar e expirar | lock e relógio PostgreSQL escolhem o estado terminal; somente a primeira transição devolve estoque | espera por lock, motivo, estado terminal e disponibilidade |
| Rajada ou abuso na borda | IAM/SigV4, resource policy, WAF e throttling de melhor esforço | logs do API Gateway/WAF e distribuição de respostas |

Os limites do API Gateway e o AWS Budget reduzem risco, mas não são garantias determinísticas de admissão ou teto de custo. O snapshot PostgreSQL do benchmark é coletado ao final e não prova ausência de lock waits durante toda a execução.

## Infraestrutura e operação

| Item | Estado |
| --- | --- |
| Java/Spring Boot | Java 21, Spring Boot 3 e arquitetura hexagonal em monólito modular. |
| Runtime local | Query API, Command API, worker, PostgreSQL, Valkey, LocalStack/SQS e Mailpit em Compose. |
| Runtime AWS demo | API Gateway REST, IAM/SigV4, WAF, VPC Link, ALB privado, ECS Fargate, RDS, Valkey, SQS/DLQs, SES, logs, alarmes e Budget. |
| Provisionamento | Recursos AWS criados por Terraform; nenhuma etapa de console faz parte do contrato. |
| Encerramento | `terraform destroy` removeu 106 recursos da demo e o state terminou vazio. |
| CI/CD | Fora do escopo desta entrega. |

## Veredito

- **Demo:** completa e validada para o escopo do case, com evidência local, estática e remota. O baseline de carga é uma fotografia local curta, não um SLO de produção.
- **High-load:** desenho de evolução Multi-AZ que conserva o mesmo core Java. Capacidade, failover, RTO/RPO e custo operacional continuam não comprovados até existir ambiente autorizado.
- **Regra de comunicação:** arquitetura descrita mostra intenção; somente teste ou observação registrada mostra comportamento.
