# Sabatina das especificações

Status: revisão documental consolidada. Este registro não aprova implementação nem transforma decisões descritas em evidência executada.

## Base examinada

- Demo: spec, contexto, design e 29 tasks.
- Alta carga: spec, contexto, design e 20 tasks.
- `STATE.md`, `README.md`, `IMPLEMENTATION_PLAN.md`, ADRs, modelo de dados, estimativa de custo e `Case BackEnd 1.md`.
- Não há código de aplicação nem infraestrutura implementada no escopo desta revisão.
- A matriz em `docs/case-requirements-evaluation.md` separa cobertura documental de comprovação por execução.

## Resultado dos questionamentos

| ID | Tema questionado | Resolução documental | Situação residual |
| --- | --- | --- | --- |
| R01 | Escopo das duas arquiteturas | Demo é a única topologia que pode ser provisionada. Alta carga é desenho, Terraform e validação estática, sem aplicação remota. | Desempenho e failover da alta carga continuam não comprovados. |
| R02 | Concorrência entre instâncias | Docker Compose prevê ao menos dois processos `command-api`; em AWS, consulta e comando são serviços distintos. | A demo econômica terá uma task por serviço; a prova multi-instância é local. |
| R03 | Reserva versus compra | O domínio termina em `PENDING`, `CANCELLED` ou `EXPIRED`; não existe pagamento nem confirmação definitiva. | Uma evolução de compra exigiria nova spec e novos estados. |
| R04 | Idempotência | Resultado de comandos é persistido por 24 horas, associado à chave e à impressão digital da requisição; conflito retorna `409`. | A implementação ainda precisa provar resposta repetida após perda de conexão. |
| R05 | Expiração e devolução | O relógio do PostgreSQL decide o vencimento; consumidor e reconciliador devolvem capacidade uma única vez, até `expiresAt + 5s` em condição saudável. | Recuperação após indisponibilidade prolongada precisa de evidência operacional. |
| R06 | Estrutura Java | A camada HTTP usa packages `controller`; módulos de evento e reserva delimitam domínio, aplicação, persistência e controllers. | Regras de dependência precisarão ser comprovadas por testes de arquitetura. |
| R07 | Ordem dos testes | Infraestrutura local precede migrations e testes de integração nas dependências das tasks. | Nenhuma. |
| R08 | Critérios e gates | Tasks ligam requisitos a testes unitários, integração, concorrência, contrato, infraestrutura e avaliação final do case. | O validador estrutural não substitui execução dos comandos. |
| R09 | Origem de AWS e Terraform | Registrados como decisão desta solução e restrição operacional informada pelo responsável, não como obrigação textual do case. | Nenhuma. |
| R10 | Orçamento e duração | Demo limitada a US$5 e 1h30; estimativa, alertas e destruição são portões explícitos. | AWS Budgets não é limite rígido de gasto. |
| R11 | Banco e cache | PostgreSQL foi comparado com DynamoDB; Valkey foi comparado com Redis OSS, cache local e caches de borda. | Reavaliar apenas com evidência de hot row ou dependência exclusiva do Redis. |
| R12 | Carga e SLO | Especificação define medições e cenários, mas não inventa capacidade comprovada sem benchmark. | Workload final, p95/p99 alcançados e custo por carga permanecem evidência futura. |
| R13 | Coerência do cache | TTL máximo de um segundo, invalidação pós-commit, fallback protegido e proibição de autorizar reserva pelo cache valem nas duas topologias. | Medir pressão do fallback no banco. |
| R14 | Degradação | Fallback tem timeout, circuit breaker e limite de concorrência por task; comandos têm prioridade sobre leituras. | Ajustar números somente a partir de teste. |
| R15 | Mensageria | Expiração e notificação usam filas e DLQs separadas; tipos de evento, retries e alarmes são explícitos. | Validar redrive e duplicidade por teste. |
| R16 | Recuperação de alta carga | Multi-AZ, filas duráveis, idempotência e reconciliação estão desenhados. | RTO, RPO, cutover e failover seguem não comprovados por decisão de não provisionar. |
| R17 | Escala de consultas e comandos | `query-api`, `command-api` e `worker` são serviços ECS independentes, todos originados da mesma imagem Java. | Políticas de escala da alta carga precisam de dados reais para calibração. |
| R18 | Conexões e réplicas | Alta carga usa RDS Proxy com endpoints de leitura e escrita; consulta de disponibilidade pode usar réplica, consulta de reserva recém-criada usa escrita para evitar `404` por atraso. | Métricas de conexão e atraso de réplica precisam de ambiente remoto. |
| R19 | Cliente da reserva | `Customer` é persistido e referenciado por `Reservation.customerId`; o modelo completo está centralizado em `docs/data-model.md`. | Política de retenção/eliminação de dados pessoais é evolução antes de produção real. |
| R20 | Notificação por e-mail | `ReservationCreated` segue por outbox, fila própria, worker e SES; falha de e-mail não altera estoque. | Sandbox do SES exige identidades verificadas; entrega é pelo menos uma vez. |
| R21 | Autenticação e abuso | API Gateway REST é o único ponto público, exige IAM/SigV4 e combina resource policy, WAF, throttling e limites de escala. | Rate limiting e Budgets são camadas de redução de risco, não garantia de custo zero. |
| R22 | Defesa contra oversell | Decremento condicional no evento e criação da reserva ocorrem na mesma transação; cancelamento e expiração competem por transição condicional, e somente o vencedor devolve capacidade. | A propriedade só pode receber PASS após testes concorrentes e de falha. |

## Decisões vigentes

- A demo é a prioridade executável e pode permanecer ligada por no máximo 1h30 dentro de US$100 em créditos. A alta carga não será aplicada nem testada remotamente nesta entrega.
- O mesmo artefato Java, regras de negócio, controllers, schema, migrations, cache e contratos HTTP roda nas duas arquiteturas. Apenas Terraform, quantidade/tamanho de recursos e configuração operacional mudam.
- `query-api`, `command-api` e `worker` são modos da mesma imagem, implantados separadamente para escalar e observar consultas, reservas e tarefas assíncronas sem bifurcar o domínio.
- Toda reserva pertence a um cliente e a um evento. A reserva criada gera e-mail assíncrono informando que ela é temporária; o e-mail não confirma compra.
- PostgreSQL é a fonte autoritativa. ElastiCache for Valkey implementa cache compatível com o protocolo Redis, com TTL curto; o cache nunca decide disponibilidade para uma reserva.
- Um API Gateway REST regional autenticado com IAM/SigV4 é o único ponto público. API key não é tratada como autenticação. Backends ficam privados atrás de VPC Link e ALB interno.
- Cada decisão arquitetural relevante está registrada em ADR autossuficiente; nenhuma escolha é aceita apenas porque foi solicitada.

## Evidência exigida antes de declarar atendimento

1. Executar testes unitários e de integração de cada regra de domínio e contrato HTTP.
2. Provar concorrência com processos distintos, incluindo mais solicitações do que capacidade, retry idempotente e corrida cancelamento/expiração.
3. Provar cache hit/miss/invalidação/fallback e confirmar que falha do cache não muda a regra de estoque.
4. Provar isolamento e redrive das filas de expiração e notificação, incluindo duplicidade de mensagens.
5. Validar Terraform da demo, estimar custo, aplicar somente com autorização e coletar smoke tests antes do `destroy` em até 1h30.
6. Manter desempenho e failover da alta carga como não comprovados enquanto não houver ambiente autorizado.

## Compatibilidade dos documentos de spec

Os critérios usam EARS em português com `QUANDO`, `SE`, `ENQUANTO` e `DEVE`. A regra textual prevalece sobre o validador original da skill, que exige cabeçalhos e uma palavra-chave de obrigação em inglês. Essa incompatibilidade conhecida não é motivo para reintroduzir mistura de idiomas. A verificação manual deve confirmar que cada critério numerado contém condição, obrigação observável e vínculo de rastreabilidade.

As specs permanecem documentos de planejamento. Nenhuma task está autorizada para implementação por esta revisão.
