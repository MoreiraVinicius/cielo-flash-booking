# Avaliação das arquiteturas contra o case

Esta avaliação compara a cobertura documental com `Case BackEnd 1.md`. “Coberto” significa que spec, design e tasks possuem uma solução verificável; não significa que código ou infraestrutura já tenham sido executados. A demo ainda precisa de evidência local e AWS. A arquitetura de alta carga será validada somente de forma estática nesta entrega.

## Requisitos funcionais

| Requisito do case | Demo | Alta carga | Evidência planejada |
| --- | --- | --- | --- |
| `POST /events` | Coberto pelo serviço de comandos. | Mesmo controller, domínio e schema da demo. | Contrato `201/400`, teste de integração e idempotência. |
| `GET /events/{id}` | Coberto pelo serviço de consultas e cache Valkey. | Mesmo código; mais tasks e Valkey Multi-AZ. | Teste de hit, miss, TTL, fallback e `404`. |
| `POST /events/{id}/reservations` | Coberto com cliente, estoque condicional e outbox. | Mesmo código; serviço de comandos escala independentemente. | Testes concorrentes com mais solicitações que capacidade. |
| `GET /reservations/{id}` | Coberto pelo serviço de consultas, incluindo cliente e motivo terminal. | Mesmo código e contrato. | Teste de contrato, cache e `404`. |
| `DELETE /reservations/{id}` | Coberto com transição condicional e devolução única. | Mesmo código e transação. | Teste de repetição e corrida com expiração. |

Resultado funcional: as duas arquiteturas cobrem os cinco endpoints no desenho. A ligação `Customer -> Reservation -> Event` e o e-mail de reserva são extensões compatíveis, sem criar novas rotas obrigatórias.

## Requisitos não funcionais

| Requisito do case | Demo | Alta carga | Limite ou prova exigida |
| --- | --- | --- | --- |
| Múltiplas instâncias simultâneas | Três serviços separados; perfil local de concorrência inicia ao menos duas instâncias de comandos. AWS econômica usa uma task por serviço. | Mínimo de duas tasks por serviço em AZs distintas. | Teste local deve atravessar processos distintos; alta carga não será comprovada remotamente agora. |
| Nunca permitir oversell | PostgreSQL faz decremento condicional e constraints; cancelamento/expiração devolvem uma vez. | Mesma transação e mesmas migrations no Aurora PostgreSQL. | Soma aceita nunca supera capacidade; `available` permanece entre zero e capacidade. |
| Expiração automática | Outbox, SQS com atraso, consumidor idempotente e reconciliador pelo relógio do banco. | Mesmo worker, com escala por backlog e Multi-AZ. | Com componentes saudáveis, devolução termina até `expiresAt + 5s`. |
| Idempotência | Registro PostgreSQL de 24 horas para todos os comandos. | Mesmo schema e comportamento. | Retry igual repete resposta; chave incompatível retorna `409`. |
| Consistência eventual da disponibilidade | Os dois GETs usam Valkey com TTL máximo de um segundo e invalidação após commit. | Mesmo contrato em Valkey Multi-AZ. | Cache nunca autoriza reserva; banco continua fonte autoritativa. |
| Tratamento explícito de erros | Problem Details, correlation ID, `400`, `403`, `404`, `409`, `429`, `500` e `503`. | Mesmo contrato HTTP. | Testes de controller e falhas de dependência. |

Resultado não funcional: o desenho cobre todos os requisitos. A demo consegue provar concorrência e consistência localmente sem pagar pela topologia de alta carga. A arquitetura alta preserva as regras, mas desempenho, failover e capacidade continuam “não comprovados” até existir ambiente autorizado.

## Restrições e preferências técnicas

| Item | Avaliação |
| --- | --- |
| Java e Spring Boot | Um binário Java 21/Spring Boot com modos `query-api`, `command-api` e `worker`. |
| APIs REST | Cinco rotas em controllers, com contratos e erros explícitos. |
| Sistemas distribuídos | Concorrência no banco, outbox, SQS, cache eventual, retries e DLQs estão cobertos. |
| Qualidade de código | Módulos por capacidade de negócio, domínio independente de AWS/Spring e regras de dependência verificáveis. |
| Testes | Unitários, integração com PostgreSQL/Valkey/mensageria, concorrência multiprocesso, contrato e Terraform. |
| Segurança | API Gateway REST com IAM/SigV4, resource policy, WAF, throttling e backends privados. |
| Docker Compose | Inclui consultas, comandos, worker, PostgreSQL, Valkey, mensageria e Mailpit. |
| README | Deve documentar build, execução, credenciais temporárias, teste, apply/destroy e uso de IA. |
| CI/CD | Continua fora da entrega imediata; é preferência da vaga, não obrigação textual do case. |

## Restrições de entrega

| Exigência do case | Cobertura nas duas arquiteturas | Estado real |
| --- | --- | --- |
| Docker Compose | A demo local inicia consultas, comandos, worker, PostgreSQL, Valkey, mensageria e Mailpit; o mesmo artefato é a base da alta carga. | Planejado nas tasks; ainda não executado. |
| Testes automatizados | Há matriz para unidade, integração, contrato, concorrência multiprocesso e infraestrutura. | Planejados; nenhum resultado pode ser presumido. |
| README e instruções | Build, execução local, autenticação, Terraform, custo e destruição estão no plano documental. | Parcial: decisões existem, comandos finais dependem da implementação. |
| Repositório GitHub | Publicação e histórico revisável fazem parte da entrega do case. | Não comprovado: este diretório não é um repositório Git nesta revisão. |
| Decisões, trade-offs e evoluções | ADRs cobrem escopo, reserva, expiração, banco, cache, idempotência, relógio, cliente, e-mail, segurança e separação operacional. | Coberto documentalmente; deve permanecer consistente com o código futuro. |
| Solução completa e funcional | A demo é a candidata executável; alta carga reutiliza o mesmo código e acrescenta apenas topologia. | Não atendido ainda, pois a implementação não foi autorizada nem realizada. |

## Funcionalidade adicional: e-mail

O e-mail confirma a criação de uma reserva temporária, não uma compra. Ele sai de `ReservationCreated` pelo outbox, SQS e SES. Falha de envio não altera estoque nem estado; retry esgotado vai para DLQ e alarme. No sandbox do SES, somente identidades verificadas participam da demonstração.

## Veredito

- **Demo:** atende documentalmente aos requisitos funcionais e não funcionais; ainda não atende como entrega executável enquanto código, testes e infraestrutura não produzirem evidência.
- **Alta carga:** atende documentalmente com o mesmo código e regras da demo; sua topologia amplia disponibilidade e escala independente, mas não pode ser declarada validada em produção nesta entrega.
- **Regra de aprovação:** nenhuma arquitetura recebe PASS apenas por estar descrita. Cada linha da matriz precisa de teste ou, quando a alta carga não for aplicada, deve permanecer marcada como evidência remota pendente.
