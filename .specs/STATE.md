# Project State

## Decisions

### AD-001 - AWS como único provedor de runtime

- **Status:** active
- **Decision:** Todos os recursos do ambiente publicado serão serviços AWS provisionados por Terraform.
- **Rationale:** AWS e Terraform são escolhas explícitas do usuário; não são exigências textuais do enunciado `Case BackEnd 1.md`. Docker Compose permanece para desenvolvimento e avaliação locais.

### AD-002 - Java e Spring Boot

- **Status:** active
- **Decision:** A aplicação usará Java 21, Spring Boot 3, Maven e arquitetura hexagonal em um monólito modular.
- **Rationale:** A escolha prioriza a stack da vaga e mantém a operação viável para uma pessoa.

### AD-003 - Duas arquiteturas executáveis

- **Status:** superseded by AD-006
- **Decision:** A solução terá um plano demo e um plano de alta carga separados. Alta carga evolui a demo sem reescrever o domínio.
- **Rationale:** Consultas e reservas têm gargalos diferentes. A evolução deve ser orientada por métricas, não por antecipação.

### AD-004 - ECS antes de EKS

- **Status:** active
- **Decision:** ECS Fargate será usado nas duas arquiteturas. EKS será apenas uma alternativa organizacional futura.
- **Rationale:** EKS aumenta a carga operacional e não resolve sozinho os hot spots do inventário.

### AD-005 - PostgreSQL como fonte de verdade inicial

- **Status:** active
- **Decision:** O inventário e as reservas usarão PostgreSQL com atualização condicional atômica.
- **Rationale:** A estratégia garante ausência de oversell com um modelo simples e auditável.

### AD-006 - Demo publicada e alta carga mantida como desenho de evolução

- **Status:** active
- **Decision:** Demonstrar a demo na AWS e manter a alta carga somente como arquitetura-alvo documentada, sem código, Terraform ou validação executável próprios nesta versão.
- **Reason:** A evolução deve nascer de gargalos medidos na demo; antecipar implementação Multi-AZ e autoscaling criaria custo e complexidade sem evidência.
- **Trade-off:** Capacidade, desempenho, failover e custo da alta carga permanecem hipóteses de desenho até uma futura implementação autorizada.
- **Scope:** Entrega, publicação e critérios de validação das duas features.

### AD-007 - Reserva temporária e encerramento auditável

- **Status:** active
- **Decision:** Manter PENDING, CANCELLED e EXPIRED, sem confirmação definitiva de compra; antes de `expiresAt`, `DELETE` encerra uma reserva pendente como CANCELLED, enquanto em `expiresAt` ou depois o prazo prevalece e o encerramento é EXPIRED; persistir código e descrição do motivo nos estados terminais.
- **Reason:** Preservar o escopo de reserva temporária e explicar seus encerramentos.
- **Scope:** Domínio compartilhado pelas duas arquiteturas.

### AD-008 - Liberação até cinco segundos após vencimento

- **Status:** active
- **Decision:** A reserva deixa de ser válida em `expiresAt`; qualquer encerramento iniciado nesse instante ou depois deve materializar EXPIRED pelo relógio do PostgreSQL, ainda que seja disparado por `DELETE`. Com banco e processamento saudáveis, concluir devolução de capacidade até `expiresAt + 5 segundos`, sem estender a validade.
- **Reason:** Limitar estoque temporariamente bloqueado e definir um prazo verificável para o fluxo assíncrono.
- **Trade-off:** Pode haver indisponibilidade temporária de ingressos vencidos dentro dessa janela; falhas exigem contrato de recuperação separado.
- **Scope:** Expiração por consumidor e reconciliador, em ambas as arquiteturas.

### AD-009 - PostgreSQL autoritativo com evolução Aurora

- **Status:** active
- **Decision:** Usar PostgreSQL para inventário, reservas, idempotência e outbox; RDS na demo e Aurora Serverless na arquitetura alta, sem mudar driver, schema, migrations ou binário Java.
- **Reason:** Transação, invariantes e auditoria reduzem risco para este domínio e prazo; DynamoDB é alternativa somente para hot row comprovado.
- **Trade-off:** Escritas concorrentes por evento podem gerar lock waits e exigem medição.
- **Scope:** Persistência das duas arquiteturas.

### AD-010 - Cache Valkey para disponibilidade do evento

- **Status:** active
- **Decision:** Cachear somente `GET /events/{id}` em ElastiCache for Valkey; `GET /reservations/{id}` consulta PostgreSQL e expõe o evento apenas como `{id, name}`.
- **Reason:** Disponibilidade de evento concentra leituras repetidas em flash sales. Reservas são consultas por identidade, mudam de estado e contêm dados pessoais; cacheá-las para reutilizar dados do evento mistura responsabilidades.
- **Trade-off:** A disponibilidade exibida pode estar defasada por até um segundo. Consultas de reserva continuam consumindo o banco e não dependem do Valkey.
- **Scope:** Consultas, ECS e infraestrutura das duas arquiteturas.

### AD-011 - Catálogo fechado de motivos de encerramento

- **Status:** active
- **Decision:** O servidor grava `CANCELLED_BY_REQUEST` somente quando `DELETE` encerra a reserva antes de `expiresAt`; em `expiresAt` ou depois grava `RESERVATION_DEADLINE_REACHED`, inclusive quando `DELETE` materializa o vencimento. As descrições são fixas e `closureReason` aparece somente em reservas terminais.
- **Reason:** O encerramento precisa ser auditável e ter contrato HTTP estável.
- **Scope:** Domínio e API compartilhados pelas duas arquiteturas.

### AD-012 - Idempotência durável e relógio transacional

- **Status:** active
- **Decision:** PostgreSQL mantém o resultado de comandos por uma janela de 24 horas medida pelo próprio relógio UTC. Durante a janela, a chave reproduz o resultado compatível ou rejeita reutilização incompatível; no vencimento, uma nova tentativa pode reivindicá-la atomicamente. Registros vencidos são removidos pelo worker em lotes limitados, sem participar da decisão de validade.
- **Reason:** Múltiplas tasks não podem depender de memória, cache, limpeza pontual ou relógios locais para preservar um único efeito. A separação entre validade lógica e remoção física evita tanto a reutilização antecipada quanto o bloqueio eterno da chave.
- **Scope:** Domínio e persistência compartilhados pelas duas arquiteturas.

### AD-013 - Exposição HTTP temporária limitada por CIDR

- **Status:** superseded by AD-014
- **Decision:** A API REST publicada da demo aceita apenas os CIDRs fornecidos a Terraform em `allowed_cidrs`, sem valor default permissivo.
- **Reason:** Credenciais AWS não são controle de acesso HTTP; a demonstração individual precisa limitar a exposição sem introduzir autenticação de produto.
- **Scope:** Borda da demo e documentação operacional.

### AD-014 - Autenticação IAM e proteção de custos na borda

- **Status:** active
- **Decision:** Um API Gateway REST regional é o único ponto público; exige IAM/SigV4 e aplica resource policy, WAF e throttling antes do VPC Link.
- **Reason:** CIDR isolado não autentica pessoas, API key não é autorização e rejeitar tráfego na borda protege containers, banco e orçamento.
- **Trade-off:** Entrevistadores precisam assinar chamadas com credenciais temporárias; limites e Budgets não garantem teto financeiro absoluto.
- **Scope:** Borda, autenticação operacional e controles de custo das duas arquiteturas.

### AD-015 - Cliente persistido e notificação de reserva

- **Status:** active
- **Decision:** Toda reserva pertence a um Customer persistido e gera e-mail assíncrono de reserva temporária por outbox, SQS e SES.
- **Reason:** A reserva precisa ser utilizável pela pessoa associada sem criar cadastro completo nem acoplar SES à transação de inventário.
- **Trade-off:** Nome e e-mail viram dados pessoais; entrega de e-mail é pelo menos uma vez e não confirma compra.
- **Scope:** Domínio, persistência, contrato HTTP e mensageria das duas arquiteturas.

### AD-016 - Serviços de consulta e comando separados com um artefato

- **Status:** active
- **Decision:** Query API, Command API e worker são serviços ECS separados, mas iniciam a mesma imagem Java e compartilham integralmente domínio, schema e contratos.
- **Reason:** Consultas e reservas escalam em momentos diferentes; deployments separados fornecem sinais e escala próprios sem duplicar regras.
- **Trade-off:** API Gateway, ALB, task definitions e observabilidade têm mais componentes que um serviço HTTP único.
- **Scope:** Empacotamento, controllers e infraestrutura das duas arquiteturas.

### AD-017 - Teto de alerta de custo da demo em US$5

- **Status:** active
- **Decision:** O AWS Budget da demo usa limite mensal de US$5 e envia alertas reais em 50%, 80% e 100% para o e-mail configurado localmente em `budget_alert_email`.
- **Reason:** O responsável reduziu o limite de gasto aceitável antes de qualquer provisionamento remoto.
- **Trade-off:** AWS Budgets atualiza custos periodicamente e alerta; não impõe interrupção imediata nem garante teto financeiro absoluto.
- **Scope:** Infraestrutura Terraform e documentação operacional da demo.

### AD-018 - Manter IAM/SigV4 e aceitar throttling de melhor esforço

- **Status:** active
- **Decision:** A demo mantém somente IAM/SigV4 como credencial de cliente. Os limites de stage do API Gateway continuam configurados, mas são documentados como metas de melhor esforço, sem alegar `429` determinístico.
- **Reason:** O teste remoto concorrente confirmou os limites efetivos, mas não observou `429`. API key e usage plan acrescentariam um segundo segredo e continuariam sujeitos a melhor esforço; um limitador determinístico expandiria a arquitetura da demo.
- **Trade-off:** O resultado de um burst não é previsível como `429`; a validação exige os limites efetivos e registra a distribuição observada. A entrega deixa explícita essa semântica em vez de ocultar o resultado real.
- **Scope:** Contrato de borda, runbook e validação da demo.

### AD-019 - PostgreSQL autoritativo para tempo e identidade persistidos

- **Status:** active
- **Decision:** Criação de reserva, expiração, janela idempotente e identidade reaproveitada de cliente usam valores persistidos e o relógio do PostgreSQL; chaves idempotentes têm limite de 128 caracteres na API e no schema.
- **Reason:** Relógios de instâncias e objetos transitórios não podem decidir estado compartilhado, e entradas sem limite não devem falhar somente no índice do banco.
- **Trade-off:** Os adaptadores JDBC expõem operações temporais explícitas e os testes de corretude exigem PostgreSQL real.
- **Scope:** Persistência e contratos HTTP da demo e da arquitetura-alvo.

### AD-020 - Persistência JDBC única e entrega de e-mail com lease

- **Status:** active
- **Decision:** Toda persistência de runtime usa Spring JDBC. O envio de e-mail adquire lease em transação curta, chama o provedor sem transação de banco aberta e registra o resultado depois; registros operacionais terminais são removidos em lotes após retenção.
- **Reason:** Uma segunda tecnologia de persistência sem benefício aumenta o custo de leitura, enquanto uma chamada remota dentro de transação prolonga locks e conexões.
- **Trade-off:** A entrega continua pelo menos uma vez em resultado ambíguo do provedor; o lease evita concorrência, mas não torna SES e PostgreSQL atomicamente coordenados.
- **Scope:** Aplicação, worker e schema compartilhados.

### AD-021 - Janela comercial autoritativa no evento

- **Status:** active
- **Decision:** Eventos podem ter `startsAt` e `endsAt` opcionais; a ausência de início torna a venda imediata e a ausência de fim não a encerra por tempo. O PostgreSQL decide a criação e a elegibilidade temporal no mesmo decremento condicional de inventário.
- **Reason:** Abertura e encerramento de flash sale precisam permanecer corretos entre tasks concorrentes sem transformar cache, worker ou relógios locais em autoridade de reserva.
- **Trade-off:** O evento não recebe estado comercial persistido nem endpoint de edição; consumidores calculam sua elegibilidade no comando e consultas podem ficar defasadas pelo TTL de cache.
- **Scope:** Domínio `Event`, contrato HTTP, schema Flyway, reserva, cache e as arquiteturas demo/high-load.

### AD-022 - `.specs` como fonte única de sistema

- **Status:** active
- **Decision:** `.specs/` é a única fonte autoritativa de comportamento, arquitetura, decisões, planos e validação atuais. Markdown fora dela só mantém entrada original, navegação concisa ou procedimento operacional que não replique a especificação.
- **Reason:** Cópias de decisões e planos divergiram da contagem real de tarefas e tornaram documentos históricos fáceis de confundir com intenção atual.
- **Trade-off:** O leitor consulta `.specs/` para detalhes de produto e arquitetura em vez de encontrar narrativas completas no README e em documentos auxiliares.
- **Scope:** Todo Markdown do repositório.

### AD-023 - Dashboard operacional único sem telemetria adicional

- **Status:** active
- **Decision:** O dashboard `flash-booking-demo-demo` reúne borda, runtime ECS, dependências de dados, filas/DLQs e investigação por Logs Insights usando exclusivamente métricas e log groups já produzidos pela demo.
- **Reason:** A operação durante a demonstração precisa permitir diagnóstico progressivo em uma única tela, sem exigir navegação prévia entre serviços nem criar instrumentação apenas para apresentação.
- **Trade-off:** O dashboard depende das convenções de nome dos recursos Terraform e widgets de Logs Insights podem gerar custo de consulta conforme a janela examinada; não há novos alarmes nem métricas customizadas.
- **Scope:** Terraform de edge/observabilidade e operação da demo AWS.

### AD-024 - Dashboard de negócio baseado em interações HTTP

- **Status:** active
- **Decision:** O dashboard `flash-booking-demo-negocio` apresenta em pt-BR a jornada e a experiência percebida usando métricas detalhadas já publicadas pelo API Gateway, com aviso explícito de que os valores representam interações e respostas HTTP.
- **Reason:** O público de negócio precisa compreender interesse, tentativas, aceite e cancelamento sem navegar por recursos técnicos nem interpretar nomenclatura de infraestrutura.
- **Trade-off:** Repetições idempotentes contam novamente e não existe informação de clientes únicos, reservas únicas, vendas ou receita; obter esses indicadores exigiria telemetria de domínio adicional fora deste escopo.
- **Scope:** Terraform de edge/observabilidade e apresentação da demo AWS.

### AD-025 - Carga dinâmica local antes de claims de produção

- **Status:** active
- **Decision:** A capacidade atual será medida com dados fake determinísticos em uma stack Docker Compose local. Smoke, stress e spike são gates obrigatórios; capacity valida por 30 minutos 60% do limite sustentável medido; load exige forecast aprovado; soak é promovido somente após os gates curtos. Uma carga AWS explicitamente autorizada será correlacionada por leitura do CloudWatch, sem liberar o harness para disparar tráfego remoto.
- **Reason:** O baseline atual é curto e não existe demanda de produção aprovada nem ambiente high-load provisionado. A separação evita transformar um benchmark local em claim de produção.
- **Trade-off:** Os resultados orientam gargalos e evolução, mas não comprovam capacidade, failover ou custo na AWS; CloudWatch correlaciona componentes em períodos de um minuto, mas não substitui as medidas por requisição do k6; RSS de container é apenas indicador de vazamento, não prova de heap JVM.
- **Scope:** Harness e evidência de performance da demo; especificação `dynamic-fake-load`.

### AD-026 - Corte automático recuperável ao atingir US$50

- **Status:** active
- **Decision:** Ao atingir US$50 de gasto mensal real, AWS Budgets publica no SNS operacional e uma Lambda interrompe os componentes recuperáveis: suspende e fixa em zero os alvos de autoscaling de `query-api` e `command-api`, reduz a zero os três serviços ECS e solicita `StopDBInstance` ao RDS PostgreSQL.
- **Reason:** Alertas apenas informam; a demo deixada ativa precisa limitar o custo posterior sem apagar dados ou tornar a retomada manualmente destrutiva.
- **Trade-off:** AWS Budgets processa custos com atraso e não é teto financeiro. ALB, Valkey, VPC, armazenamento, WAF e API Gateway seguem provisionados e podem gerar custo residual. RDS é reiniciado automaticamente pela AWS após no máximo sete dias parado.
- **Scope:** Budget, SNS, Lambda de corte e permissões mínimas da demo AWS.

## Handoff

- **Feature**: `flash-booking-demo`
- **Phase / Task**: Execute / DEMO-11 concluída e validada.
- **Completed**: DEMO-11, desenho de Budget→SNS→Lambda, AD-026 e T33/T35 foram aplicados. O plano inicial criou 8 recursos e atualizou 1; a correção direcionada atualizou somente a Lambda para separar ARN e nome do cluster. A verificação independente aprovou 7/7 critérios, os testes unitários, `terraform test`, `terraform validate`, validadores das specs, leitura remota e sensor de discriminação (1/1 mutação eliminada).
- **In-progress**: Nenhum.
- **Next step**: Nenhum.
- **Blockers**: Nenhum.
- **Uncommitted files**: Alterações locais preexistentes fora do escopo permanecem preservadas e não serão incluídas na entrega.
- **Branch**: `main`
