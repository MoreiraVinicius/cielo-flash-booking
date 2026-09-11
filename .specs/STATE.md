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

### AD-006 - Demo publicada e alta carga entregue sem provisionamento remoto

- **Status:** active
- **Decision:** Demonstrar a demo na AWS e entregar também o código da alta carga, sem aplicar o Terraform nem testar remotamente a alta carga.
- **Reason:** Há poucas horas disponíveis, US$100 em créditos AWS e a demo ficará ligada por no máximo 1h30, conforme a restrição operacional informada.
- **Trade-off:** Não haverá evidência de funcionamento da infraestrutura, desempenho ou failover da alta carga na AWS.
- **Scope:** Entrega, publicação e critérios de validação das duas features.
- **ADR:** `docs/adr/0001-demo-publicada-alta-carga-sem-provisionamento.md`

### AD-007 - Reserva temporária e encerramento auditável

- **Status:** active
- **Decision:** Manter PENDING, CANCELLED e EXPIRED, sem confirmação definitiva de compra; persistir código e descrição do motivo nos estados terminais.
- **Reason:** Preservar o escopo de reserva temporária e explicar seus encerramentos.
- **Scope:** Domínio compartilhado pelas duas arquiteturas.
- **ADR:** [ADR 0002](../docs/adr/0002-reserva-temporaria-com-motivo-de-encerramento.md).

### AD-008 - Liberação até cinco segundos após vencimento

- **Status:** active
- **Decision:** Concluir devolução de capacidade até expiresAt + 5 segundos com banco e processamento de expiração saudáveis, sem estender a validade.
- **Reason:** Limitar estoque temporariamente bloqueado e definir um prazo verificável para o fluxo assíncrono.
- **Trade-off:** Pode haver indisponibilidade temporária de ingressos vencidos dentro dessa janela; falhas exigem contrato de recuperação separado.
- **Scope:** Expiração por consumidor e reconciliador, em ambas as arquiteturas.
- **ADR:** [ADR 0003](../docs/adr/0003-prazo-de-liberacao-de-reservas-expiradas.md).

### AD-009 - PostgreSQL autoritativo com evolução Aurora

- **Status:** active
- **Decision:** Usar PostgreSQL para inventário, reservas, idempotência e outbox; RDS na demo e Aurora Serverless na arquitetura alta, sem mudar driver, schema, migrations ou binário Java.
- **Reason:** Transação, invariantes e auditoria reduzem risco para este domínio e prazo; DynamoDB é alternativa somente para hot row comprovado.
- **Trade-off:** Escritas concorrentes por evento podem gerar lock waits e exigem medição.
- **Scope:** Persistência das duas arquiteturas.
- **ADR:** [ADR 0004](../docs/adr/0004-postgresql-como-fonte-autoritativa.md).

### AD-010 - Cache Valkey compartilhado desde a demo

- **Status:** active
- **Decision:** Cachear os dois GETs em ElastiCache for Valkey com o mesmo binário Java; alterar topologia e capacidade apenas por Terraform.
- **Reason:** Reduz picos de leitura, mantém comportamento entre ambientes e permite escalar sem alterar código.
- **Trade-off:** Leituras podem estar defasadas por até um segundo; cache indisponível pressiona o PostgreSQL até o limite de fallback.
- **Scope:** Consultas, ECS e infraestrutura das duas arquiteturas.
- **ADR:** [ADR 0005](../docs/adr/0005-cache-valkey-compartilhado-e-binario-unico.md).

### AD-011 - Catálogo fechado de motivos de encerramento

- **Status:** active
- **Decision:** O servidor grava `CANCELLED_BY_REQUEST` ou `RESERVATION_DEADLINE_REACHED`, com descrição fixa, e expõe `closureReason` somente em reservas terminais.
- **Reason:** O encerramento precisa ser auditável e ter contrato HTTP estável.
- **Scope:** Domínio e API compartilhados pelas duas arquiteturas.
- **ADR:** [ADR 0006](../docs/adr/0006-catalogo-de-motivos-de-encerramento.md).

### AD-012 - Idempotência durável e relógio transacional

- **Status:** active
- **Decision:** PostgreSQL mantém o resultado de comandos por 24 horas e decide a elegibilidade de expiração pelo próprio relógio UTC.
- **Reason:** Múltiplas tasks não podem depender de memória, cache ou relógios locais para preservar um único efeito e impedir expiração antecipada.
- **Scope:** Domínio e persistência compartilhados pelas duas arquiteturas.
- **ADR:** [ADR 0007](../docs/adr/0007-idempotencia-persistente-de-comandos.md) e [ADR 0008](../docs/adr/0008-relogio-do-banco-para-expiracao.md).

### AD-013 - Exposição HTTP temporária limitada por CIDR

- **Status:** superseded by AD-014
- **Decision:** A API REST publicada da demo aceita apenas os CIDRs fornecidos a Terraform em `allowed_cidrs`, sem valor default permissivo.
- **Reason:** Credenciais AWS não são controle de acesso HTTP; a demonstração individual precisa limitar a exposição sem introduzir autenticação de produto.
- **Scope:** Borda da demo e documentação operacional.
- **ADR:** [ADR 0009](../docs/adr/0009-restringir-demo-por-cidr-no-api-gateway.md).

### AD-014 - Autenticação IAM e proteção de custos na borda

- **Status:** active
- **Decision:** Um API Gateway REST regional é o único ponto público; exige IAM/SigV4 e aplica resource policy, WAF e throttling antes do VPC Link.
- **Reason:** CIDR isolado não autentica pessoas, API key não é autorização e rejeitar tráfego na borda protege containers, banco e orçamento.
- **Trade-off:** Entrevistadores precisam assinar chamadas com credenciais temporárias; limites e Budgets não garantem teto financeiro absoluto.
- **Scope:** Borda, autenticação operacional e controles de custo das duas arquiteturas.
- **ADR:** [ADR 0012](../docs/adr/0012-autenticacao-e-protecao-de-custos-na-borda.md).

### AD-015 - Cliente persistido e notificação de reserva

- **Status:** active
- **Decision:** Toda reserva pertence a um Customer persistido e gera e-mail assíncrono de reserva temporária por outbox, SQS e SES.
- **Reason:** A reserva precisa ser utilizável pela pessoa associada sem criar cadastro completo nem acoplar SES à transação de inventário.
- **Trade-off:** Nome e e-mail viram dados pessoais; entrega de e-mail é pelo menos uma vez e não confirma compra.
- **Scope:** Domínio, persistência, contrato HTTP e mensageria das duas arquiteturas.
- **ADR:** [ADR 0010](../docs/adr/0010-cliente-como-entidade-da-reserva.md) e [ADR 0011](../docs/adr/0011-notificacao-assincrona-de-reserva-por-email.md).

### AD-016 - Serviços de consulta e comando separados com um artefato

- **Status:** active
- **Decision:** Query API, Command API e worker são serviços ECS separados, mas iniciam a mesma imagem Java e compartilham integralmente domínio, schema e contratos.
- **Reason:** Consultas e reservas escalam em momentos diferentes; deployments separados fornecem sinais e escala próprios sem duplicar regras.
- **Trade-off:** API Gateway, ALB, task definitions e observabilidade têm mais componentes que um serviço HTTP único.
- **Scope:** Empacotamento, controllers e infraestrutura das duas arquiteturas.
- **ADR:** [ADR 0013](../docs/adr/0013-separar-servicos-de-consulta-e-comando.md).

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
- **Trade-off:** Edge-3 e Edge-4 permanecem falhos contra a especificação atual. A entrega registra esse desvio em vez de ocultá-lo com um teste ou script ad hoc.
- **Scope:** Contrato de borda, runbook e validação da demo.

## Handoff

- **Feature**: `flash-booking-demo`
- **Phase / Task**: Phase 5 / T29 - complete; provider-accurate throttling validation passed
- **Completed**: T01 (`37e45ca`), T02 (`ae2efcc`), T03 (`e055f13`), T04 (`fbab81e`), T05 (`6a1fd2e`), T06 (`0f8054c`), T07 (`3bea022`), T08 (`80cdb2e`), T09 (`523795c`), T10 (`434d88c`), T11 (`e0a3eab`), T12 (`b246f53`), Phase 2 review corrections (`ed6e50e`), T13 (`081b287`), T14 (`ad40a41`), T15 (`52ef303`), T16 (`cd66dd3`), T17 (`6c8be2a`), T18 (`f2eaead`), T19 (`1ce4696`), T20 (`da302ec`), T21 (`45356bd`), T22 (`3cdbfca`), T23 (`f327db7`), T24 (`a6535dd`), T25 (`f43635d`), T26 (`53f696e`), T27 (this commit)
- **In-progress**: Nenhum. T29 passou com a validação de throttling alinhada à semântica de melhor esforço do API Gateway. A demo foi destruída e o state foi verificado vazio.
- **Next step**: Nenhum para a entrega atual. Se uma nova demonstração precisar ser aplicada, trate a identidade SES preexistente fora do state como decisão operacional antes do apply.
- **Blockers**: Nenhum para a entrega validada. Uma nova aplicação pode exigir importar ou gerir condicionalmente a identidade SES existente fora do state. Docker Engine está saudável e Terraform é `1.16.1` com AWS provider `6.64.0`.
- **Uncommitted files**: Os artefatos desta conclusão serão commitados nesta tarefa; `demo.tfvars` permanece ignorado e não contém credenciais commitadas.
- **Branch**: `main`
