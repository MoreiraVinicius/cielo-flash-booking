# Prompts para diagramas de arquitetura AWS

Os prompts abaixo foram escritos a partir do Terraform atual e das decisões registradas em `.specs/STATE.md`.

- A **demo** representa os recursos existentes em `infra/bootstrap/`, `infra/environments/demo/` e `infra/modules/`.
- A **high-load** representa a arquitetura-alvo documentada em `.specs/features/flash-booking-high-load/`. No estado atual do repositório, `infra/environments/high-load/` ainda não existe; portanto, o diagrama deve identificá-la como planejada e não provisionada.

## 1. Arquitetura cloud AWS — demo

```text
Crie um diagrama profissional de arquitetura cloud AWS, em formato horizontal 16:9, com fundo claro, ícones oficiais da AWS, textos legíveis em português e fluxo principal da esquerda para a direita. O título deve ser “Flash Booking — Arquitetura AWS Demo”. O desenho deve representar somente a infraestrutura provisionada pelo Terraform descrito abaixo, sem adicionar Kubernetes, Lambda, CloudFront, Route 53, Cognito, DynamoDB ou qualquer serviço não citado.

Mostre fora da AWS um ator “Operador / cliente da API”, usando credenciais temporárias e assinando requisições HTTPS com IAM/SigV4. A entrada pública deve ser uma única “Amazon API Gateway REST API Regional”, protegida por uma resource policy que exige a role “ApiInvokerRole” e CIDRs permitidos. Associe “AWS WAF” ao stage demo, com rate limiting por IP. Indique throttling separado: GET com rate 20 e burst 40; POST/DELETE com rate 5 e burst 10.

Dentro de uma boundary “AWS Region”, desenhe uma VPC distribuída em duas Availability Zones. Em cada AZ mostre uma subnet pública, uma subnet privada de aplicação e uma subnet isolada de dados. As workloads não possuem IP público. Há um Internet Gateway e apenas um NAT Gateway, localizado na subnet pública da primeira AZ, compartilhado pelas duas subnets privadas de aplicação. Deixe visualmente explícito que “NAT único” e os componentes Single-AZ são escolhas econômicas da demo, não alta disponibilidade.

Ligue o API Gateway a um “API Gateway VPC Link V2”, depois a um “Application Load Balancer interno” na porta HTTP 80. O ALB deve ter dois target groups e regras de roteamento por método e caminho:
- GET /events/{id} e GET /reservations/{id} -> target group “query-api”;
- POST /events, POST /events/{id}/reservations e DELETE /reservations/{id} -> target group “command-api”.
O endpoint /actuator/health é usado apenas para health check dos target groups e não é exposto publicamente.

Na camada de compute, mostre um “Amazon ECS Cluster — AWS Fargate” com três serviços privados e desired count 1 para cada um: “query-api”, “command-api” e “worker”. Os três executam a mesma imagem Java 21 / Spring Boot 3, obtida de um repositório “Amazon ECR” com tags imutáveis, scan on push, criptografia AES-256 e lifecycle mantendo as cinco imagens mais recentes. query-api e command-api recebem tráfego do ALB; worker não fica atrás do ALB. Mostre deployment circuit breaker com rollback para os três serviços, task definitions separadas, IAM task roles separadas e uma execution role que lê a senha gerenciada do banco no AWS Secrets Manager.

Na camada de dados, em subnets isoladas, mostre:
- “Amazon RDS for PostgreSQL 16”, instância db.t4g.micro, 20 GB gp3 com autoscaling até 30 GB, criptografada, privada, Single-AZ, backup de 1 dia, credencial mestra gerenciada pelo Secrets Manager e sem deletion protection;
- “Amazon ElastiCache for Valkey 7.2”, cache.t4g.micro, um único nó, privado, criptografia em trânsito e em repouso, sem Multi-AZ e sem failover automático.

Conecte query-api ao PostgreSQL por JDBC/TLS e ao Valkey por RESP/TLS. Conecte command-api ao PostgreSQL por JDBC/TLS e ao Valkey para invalidação pós-commit. Conecte worker ao PostgreSQL para consumir a transactional outbox e executar reconciliação.

Na camada assíncrona, mostre duas filas Amazon SQS criptografadas e independentes: “Expiration Queue” e “Notification Queue”. Cada fila possui sua própria DLQ, retenção de 14 dias na DLQ e redrive após cinco recebimentos. O worker publica eventos da outbox nas filas, consome ambas, processa expiração de reservas e envia e-mail através de uma identidade verificada “Amazon SES”. Deixe claro que falha de e-mail não reverte a reserva.

Mostre observabilidade e governança sem poluir o fluxo principal:
- Amazon CloudWatch Logs para query-api, command-api, worker e access logs do API Gateway, todos com retenção de 7 dias;
- ECS Container Insights;
- alarmes CloudWatch para API Gateway 5xx, throttling e targets não saudáveis do ALB;
- dashboard CloudWatch da demo;
- AWS Budgets com orçamento mensal de US$ 5 e alertas em 50%, 80% e 100%.

Mostre security groups como controles resumidos: VPC Link -> ALB:8080; ALB -> ECS:8080; ECS -> RDS:5432; ECS -> Valkey:6379; ECS -> saída HTTPS:443. Não desenhe acesso público direto ao ALB, ECS, RDS ou Valkey.

Em uma pequena área lateral “Terraform / plano de gerenciamento”, mostre um bucket Amazon S3 privado, versionado e criptografado com AES-256 para o state, com public access block e prevent_destroy. Não conecte o bucket ao fluxo de requisições da aplicação.

Use setas sólidas para o fluxo síncrono, setas tracejadas para mensageria/observabilidade e uma legenda curta. Destaque visualmente quatro zonas: Edge, Rede privada, Compute e Dados/Mensageria. Priorize precisão técnica e legibilidade sobre decoração.
```

## 2. Arquitetura cloud AWS — high-load

```text
Crie um diagrama profissional de arquitetura cloud AWS, em formato horizontal 16:9, com fundo claro, ícones oficiais da AWS, textos legíveis em português e fluxo principal da esquerda para a direita. O título deve ser “Flash Booking — Arquitetura AWS High-Load (alvo planejado)”. Inclua no topo um selo bem visível: “Arquitetura-alvo; Terraform high-load ainda não provisionado nem validado remotamente”. O diagrama representa a evolução planejada por Terraform da demo e não deve alegar medições reais de desempenho, failover ou capacidade.

Preserve o contrato da demo: um único artefato Java 21 / Spring Boot 3, o mesmo domínio, schema, migrations, endpoints HTTP, autenticação IAM/SigV4, outbox, SQS e SES. Não adicione EKS, Lambda, CloudFront, Route 53, Cognito, DynamoDB, Kafka, MSK ou reserva assíncrona. DynamoDB, SQS FIFO e EKS podem aparecer apenas em uma nota discreta “alternativas futuras mediante nova decisão”, nunca como parte da arquitetura ativa.

Mostre fora da AWS um ator “Operador / cliente da API”, usando credenciais temporárias e HTTPS assinado com IAM/SigV4. A entrada pública deve ser uma única “Amazon API Gateway REST API Regional”, com resource policy, role de invocação com menor privilégio, CIDRs permitidos, AWS WAF e throttling/controle de admissão separados para consultas e comandos. O edge deve rejeitar excesso antes de alcançar containers ou banco.

Dentro de uma boundary “AWS Region”, desenhe uma VPC altamente disponível distribuída em pelo menos duas Availability Zones. Em cada AZ mostre subnet pública, subnet privada de aplicação e subnet isolada de dados. Mostre um NAT Gateway por AZ, workloads sem IP público e rotas simétricas. O API Gateway conecta-se por “VPC Link V2” a um “Application Load Balancer interno” Multi-AZ.

No ALB, mostre target groups independentes e roteamento:
- GET /events/{id} e GET /reservations/{id} -> “ECS Fargate query-api, 2..N tasks”;
- POST /events, POST /events/{id}/reservations e DELETE /reservations/{id} -> “ECS Fargate command-api, 2..N tasks”.
Mostre também “ECS Fargate worker, 2..N tasks”, sem exposição pelo ALB. Distribua no mínimo duas tasks de cada serviço entre AZs distintas. Os três serviços usam a mesma imagem imutável no Amazon ECR, mas possuem task definitions, IAM roles, deployments e políticas de escala independentes.

Represente as políticas de escala como caixas de controle conectadas ao respectivo serviço:
- query-api: requisições, p95, CPU e cache hit rate;
- command-api: requisições, p95, CPU, conexões e pré-escala agendada antes da flash sale;
- worker: backlog por task e idade da mensagem mais antiga, separando expiração e notificação.
Indique scale-out agressivo, cooldown de scale-in maior e máximos limitados pelo envelope validado do banco. Se lock waits ou p99 ultrapassarem o SLO, o sistema deve aplicar admissão/429/503 em vez de aumentar indefinidamente command-api.

Na camada de dados, em subnets isoladas e Multi-AZ, mostre:
- “Amazon Aurora PostgreSQL Serverless v2” como fonte autoritativa, com um writer, ao menos um reader em AZ distinta, ACUs parametrizadas, backups e failover;
- “Amazon RDS Proxy” com endpoint read-write e endpoint read-only;
- “Amazon ElastiCache for Valkey Multi-AZ”, privado, criptografado, com primary, réplica e failover automático.

Desenhe os fluxos de dados com precisão:
- query-api consulta Valkey por cache-aside;
- em cache miss de GET /events/{id}, query-api usa o endpoint read-only do RDS Proxy e Aurora readers;
- GET /reservations/{id} usa o endpoint read-write do RDS Proxy para evitar 404 transitório após criação;
- command-api e worker usam o endpoint read-write do RDS Proxy e o Aurora writer;
- command-api invalida o Valkey somente após commit;
- o cache nunca autoriza reserva; o decremento condicional e as constraints no Aurora impedem oversell;
- em falha de cache, fallback ao PostgreSQL é limitado por timeout, circuito e no máximo cinco fallbacks simultâneos por task; excedentes recebem 503.

Na camada assíncrona, mantenha duas filas Amazon SQS independentes e criptografadas: “Expiration Queue” e “Notification Queue”, cada uma com sua DLQ e alarmes próprios. O worker lê a transactional outbox no Aurora, publica e consome mensagens, reconcilia reservas vencidas e envia e-mails via Amazon SES. Expiração e notificação devem ter listeners, limites de concorrência e métricas separados para que SES lento não retenha capacidade de expiração.

Mostre Amazon CloudWatch como plano de observabilidade com métricas e dashboards separados por query-api, command-api, worker, rota, resultado e dependência. Inclua TPS, p95, p99, erros, 409/429/503, conexões, lock waits, cache hit rate, backlog, idade da mensagem, DLQ e saúde de targets. Ligue essas métricas às políticas de Application Auto Scaling e aos alarmes. Mostre que o Terraform high-load usa state separado do ambiente demo.

Use setas sólidas para requisições síncronas, setas tracejadas para eventos e métricas, e cores diferentes para leitura, comando e processamento assíncrono. Inclua uma legenda. Destaque visualmente Edge, VPC/AZs, Compute escalável, Dados Multi-AZ, Mensageria e Observabilidade. Priorize legibilidade e fidelidade ao plano Terraform; não invente números de TPS ou SLOs já comprovados.
```

## 3. C4 Model — demo

```text
Crie um diagrama C4 Model no nível “Deployment”, com detalhes de “Container”, para a solução “Flash Booking — Demo AWS”. Use notação C4 consistente e ícones AWS apenas como apoio visual. Formato horizontal 16:9, fundo claro, textos em português, relações direcionadas e protocolos nas setas. Inclua uma legenda com os tipos C4: Person, Software System, Container, Deployment Node e Infrastructure Node. O foco é mostrar onde os containers da aplicação são implantados pelos módulos Terraform e com quais serviços AWS se relacionam.

Pessoa:
- “Operador / consumidor da API”: usa credenciais temporárias e invoca a API via HTTPS com IAM/SigV4.

Software System boundary:
- “Flash Booking”: sistema de eventos e reservas temporárias.

Deployment nodes e infrastructure nodes:
- “AWS Region”.
- “Amazon API Gateway REST Regional”: único endpoint público, stage demo, autorização AWS_IAM, resource policy limitada à ApiInvokerRole e CIDRs autorizados.
- “AWS WAF”: associado ao stage, rate limiting por IP.
- “VPC em 2 Availability Zones”, contendo subnets públicas, subnets privadas de aplicação e subnets isoladas de dados.
- “Internet Gateway” e “NAT Gateway único na AZ A” para saída das subnets privadas; marque o NAT como limitação econômica da demo.
- “API Gateway VPC Link V2”.
- “Application Load Balancer interno” com target groups query-api e command-api.
- “Amazon ECS Cluster / Fargate”.
- “Amazon RDS for PostgreSQL 16 — Single-AZ”.
- “Amazon ElastiCache for Valkey 7.2 — nó único, sem failover”.
- “Amazon SQS Expiration Queue + Expiration DLQ”.
- “Amazon SQS Notification Queue + Notification DLQ”.
- “Amazon SES — identidade de remetente”.
- “AWS Secrets Manager — credencial gerenciada do RDS”.
- “Amazon ECR — imagem imutável, scan on push”.
- “Amazon CloudWatch — logs, Container Insights, alarmes e dashboard”.
- “AWS Budgets — US$ 5, alertas 50%/80%/100%”.

Containers C4 implantados no node ECS/Fargate, todos derivados da mesma imagem Java 21 / Spring Boot 3 no ECR:
- “Query API [Container]”: perfil query-api, 1 task, expõe GET /events/{id} e GET /reservations/{id}; consulta reservas no PostgreSQL e usa Valkey somente para disponibilidade de evento.
- “Command API [Container]”: perfil command-api, 1 task, expõe POST /events, POST /events/{id}/reservations e DELETE /reservations/{id}, grava no PostgreSQL e invalida cache após commit.
- “Worker [Container]”: perfil worker, 1 task, processa transactional outbox, expiração, reconciliação e notificação; usa PostgreSQL, SQS e SES; não recebe tráfego do ALB.

Relações C4 obrigatórias:
- Operador -> API Gateway: “Invoca API, HTTPS + IAM/SigV4”.
- WAF -> API Gateway stage: “Protege e limita por IP”.
- API Gateway -> VPC Link -> ALB interno: “HTTP proxy privado”.
- ALB -> Query API: “GET, HTTP:8080”.
- ALB -> Command API: “POST/DELETE, HTTP:8080”.
- Query API -> Valkey: “Cache-aside de disponibilidade de evento, RESP/TLS”.
- Query API -> PostgreSQL: “Leitura, JDBC/TLS”.
- Command API -> PostgreSQL: “Transação autoritativa + outbox, JDBC/TLS”.
- Command API -> Valkey: “Invalidação pós-commit, RESP/TLS”.
- Worker -> PostgreSQL: “Outbox e reconciliação, JDBC/TLS”.
- Worker -> SQS queues: “Publica e consome eventos, AWS API/HTTPS”.
- Cada SQS queue -> sua DLQ: “Redrive após 5 recebimentos”.
- Worker -> SES: “Envia e-mail, AWS API/HTTPS”.
- ECS task definitions -> Secrets Manager: “Obtém usuário/senha do banco”.
- ECR -> os três containers: “Fornece a mesma imagem imutável”.
- Containers e API Gateway -> CloudWatch: “Logs e métricas”.

Dentro do ALB, anote as rotas GET para query-api e POST/DELETE para command-api. Mostre que /actuator/health serve apenas aos health checks internos. Indique que RDS e Valkey ficam nas subnets isoladas; ALB e ECS ficam privados e não recebem IP público. Resuma security groups como relações permitidas, sem desenhar regras irrelevantes.

Em uma área separada, fora do Software System e sem seta de runtime, mostre “Terraform State [Deployment Support]” em um bucket S3 privado, versionado, criptografado com AES-256, public access block e prevent_destroy.

Não adicione componentes que não existem no Terraform atual. Não represente a demo como Multi-AZ no banco/cache e não mostre autoscaling: cada serviço ECS possui exatamente uma task no ambiente demo.
```

## 4. C4 Model — high-load

```text
Crie um diagrama C4 Model no nível “Deployment”, com detalhes de “Container”, para “Flash Booking — High-Load AWS”. Use notação C4 consistente e ícones AWS apenas como apoio. Formato horizontal 16:9, fundo claro, textos em português, relações direcionadas e protocolos nas setas. Inclua legenda com Person, Software System, Container, Deployment Node e Infrastructure Node. Exiba um selo no topo: “Arquitetura-alvo planejada; não provisionada e sem evidência remota de carga/failover”.

Pessoa:
- “Operador / consumidor da API”: usa credenciais temporárias e HTTPS com IAM/SigV4.

Software System boundary:
- “Flash Booking”: preserva domínio, endpoints, schema e migrations da demo; adaptadores operacionais evoluem no mesmo artefato Java.

Deployment nodes e infrastructure nodes planejados por Terraform:
- “AWS Region”.
- “Amazon API Gateway REST Regional”: único endpoint público, AWS_IAM, resource policy, CIDRs permitidos, throttling e controle de admissão.
- “AWS WAF”: rate limiting antes do compute.
- “VPC Multi-AZ”, com pelo menos duas AZs; cada AZ possui subnet pública, subnet privada de aplicação, subnet isolada de dados e NAT Gateway próprio.
- “API Gateway VPC Link V2”.
- “Application Load Balancer interno Multi-AZ”, com target groups query-api e command-api.
- “Amazon ECS Cluster / Fargate”.
- “Amazon Aurora PostgreSQL Serverless v2 Multi-AZ”: writer e ao menos um reader, ACUs e backup parametrizados.
- “Amazon RDS Proxy”: endpoint read-write e endpoint read-only.
- “Amazon ElastiCache for Valkey Multi-AZ”: primary, réplica, criptografia e failover automático.
- “Amazon SQS Expiration Queue + Expiration DLQ”.
- “Amazon SQS Notification Queue + Notification DLQ”.
- “Amazon SES”.
- “AWS Secrets Manager”.
- “Amazon ECR”.
- “Amazon CloudWatch + Application Auto Scaling”.
- “Terraform state high-load separado do state demo”.

Containers C4 implantados no ECS/Fargate, todos executando a mesma imagem Java 21 / Spring Boot 3 do ECR:
- “Query API [Container] — 2..N tasks”: serve os dois GETs; usa cache apenas para evento e escala por requisições, p95, CPU e hit rate de disponibilidade.
- “Command API [Container] — 2..N tasks”: serve POST/DELETE; escala por requisições, p95, CPU e conexões, além de scheduled pre-scaling antes da flash sale; o máximo é limitado pelo envelope do writer.
- “Worker [Container] — 2..N tasks”: outbox, expiração, reconciliação e notificação; escala por backlog por task e idade da mensagem, com concorrência separada por fila.
Distribua no mínimo duas instâncias de cada container entre AZs distintas. Mostre deployment, task role e política de escala independentes, apesar de compartilharem o mesmo artefato.

Relações C4 obrigatórias:
- Operador -> API Gateway: “Invoca API, HTTPS + IAM/SigV4”.
- WAF -> API Gateway: “Proteção e rate limiting”.
- API Gateway -> VPC Link -> ALB: “HTTP proxy privado”.
- ALB -> Query API: “GET, HTTP:8080”.
- ALB -> Command API: “POST/DELETE, HTTP:8080”.
- Query API -> Valkey: “Cache-aside de disponibilidade de evento, RESP/TLS”.
- Query API -> RDS Proxy read-only -> Aurora readers: “GET de disponibilidade em cache miss, JDBC/TLS”.
- Query API -> RDS Proxy read-write -> Aurora writer: “GET de reserva com read-after-write, JDBC/TLS”.
- Command API -> RDS Proxy read-write -> Aurora writer: “Transações autoritativas e outbox, JDBC/TLS”.
- Command API -> Valkey: “Invalidação pós-commit”.
- Worker -> RDS Proxy read-write -> Aurora writer: “Outbox, expiração e reconciliação”.
- Worker <-> filas SQS: “Publica/consome eventos, AWS API/HTTPS”.
- Cada fila -> DLQ própria: “Redrive e retenção”.
- Worker -> SES: “E-mail de reserva temporária”.
- Secrets Manager -> task definitions/RDS Proxy: “Credenciais”.
- ECR -> os três containers: “Mesma imagem imutável”.
- CloudWatch -> Application Auto Scaling -> cada serviço ECS: “Métricas e escala independentes”.

Adicione notas C4 curtas junto às relações críticas:
- “Valkey é eventual e nunca autoriza reserva”.
- “Aurora writer aplica decremento e transições condicionais; oversell permanece proibido”.
- “RDS Proxy controla conexões, mas não remove contenção da linha quente”.
- “Quando lock waits ou p99 excedem o SLO, API Gateway/WAF aplicam 429/503 em vez de escalar comandos indefinidamente”.
- “Expiração e notificação possuem filas, limites e métricas separados”.

Na observabilidade, agrupe logs, métricas, alarmes e dashboards por Query API, Command API e Worker. Mostre TPS, p95, p99, 409/429/503, conexões, lock waits, cache hit rate, backlog, idade da mensagem, DLQs e saúde do ALB. Use setas tracejadas para telemetria e eventos, setas sólidas para chamadas síncronas e cores distintas para leitura, comando e trabalho assíncrono.

Não desenhe EKS, DynamoDB ou SQS FIFO como componentes ativos. Eles podem constar apenas em uma nota externa “opções futuras dependentes de nova ADR”. Não invente valores de TPS, SLO, ACU, número máximo de tasks ou resultados de failover; esses valores ainda dependem de baseline e validação futura.
```
