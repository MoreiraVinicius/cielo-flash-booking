# Resumo executivo da performance do evento

**Status:** Implementado, verificado independentemente (44/44 criterios) e publicado na demo AWS. A chave global foi ativada em 2026-09-29; a entrega Discord e a inferencia Bedrock aguardam o fechamento de um evento elegivel.

## Problem Statement

Ao terminar a janela comercial de um evento, a demonstracao nao mostra em linguagem simples como as reservas ocuparam a capacidade e em que ritmo isso ocorreu. O publico nao tecnico precisa entender o resultado do evento sem interpretar estados internos, metricas HTTP ou alarmes de infraestrutura. Uma chave global de ativacao permite limitar a geracao e o custo ao periodo da apresentacao, e o resumo salvo pode ser enviado ao canal Discord configurado.

## Goals

- [x] Permitir ativacao manual global, desligada por padrao, mantendo a geracao e publicacao automatica no encerramento dos eventos elegiveis.
- [x] Mostrar capacidade, ingressos em reservas aceitas, reservas validas no fechamento, primeiro esgotamento temporario e ritmo de reservas.
- [x] Produzir um template curto e verificavel, com no maximo uma tentativa Bedrock para a leitura textual de cada evento ativado.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Compra confirmada, pagamento, receita e conversao | O contrato executavel registra reservas temporarias; AD-031 ainda e proposta. |
| Demanda total ou vendas perdidas | Tentativas rejeitadas nao sao persistidas por evento. |
| Diagnostico ou causa-raiz de incidentes | Alarmes do ambiente nao provam impacto causal no evento. |
| Resumo consolidado de varios eventos | Exigiria uma unidade de agregacao e um encerramento proprios. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Significado de esgotamento | Primeira vez em que a disponibilidade do evento chega a zero durante a janela; nao e compra concluida. | E o marco que o inventario atual consegue provar. | inferred |
| Escopo da feature flag | Uma chave global persistida, alterada por `PUT /executive-summary/activation`; default `false`. | O operador ativa a capacidade de relatorios para o periodo da apresentacao sem configurar cada evento. | user-confirmed |
| Elegibilidade temporal | O evento so gera resumo se sua janela comercial comecar durante a janela atual de ativacao global e permanecer elegivel ate `endsAt`. | Evita apuracao incompleta de eventos em andamento e geracao retroativa em lote. | inferred |
| Controle manual | A ativacao/desativacao muda apenas a chave global; nao cria relatorio imediatamente. | O fechamento em `endsAt` continua sendo o gatilho arquitetural. | inferred |
| Fim comercial | Somente `endsAt` conclui o evento para este relatorio. | Esgotamento pode ser temporario; evento sem `endsAt` nao possui conclusao automatica. | inferred |
| Canal de leitura | `GET /events/{id}/executive-summary` sob IAM/SigV4. | Consulta o artefato sem chamar IA novamente. | inferred |
| Canal de publicacao | Discord Incoming Webhook, um destino configurado em AWS Secrets Manager. | E uma entrega de mao unica adequada ao resumo e nao requer bot sempre conectado. | inferred |
| Configuracao do Discord | O operador cria um Incoming Webhook no canal, guarda a URL como `SecretString` no AWS Secrets Manager e informa somente seu ARN em `discord_webhook_secret_arn` no arquivo local `infra/environments/demo/demo.tfvars`. | A URL pode publicar no canal; seu valor fica fora do Terraform e do repositorio. | inferred |
| Webhook ausente ou indisponivel | Persistir o relatorio e indicar publicacao `NOT_CONFIGURED`, `FAILED` ou `UNKNOWN`; nao repetir automaticamente. | A falha de entrega nao elimina os dados nem gera mensagem duplicada. | inferred |
| Modelo | Amazon Nova Micro, `amazon.nova-micro-v1:0`, como default em `sa-east-1`; permite sobrescrita por configuracao. | AWS o descreve como modelo textual otimizado para baixo custo e sumarizacao, com suporte regional em Sao Paulo. O prompt tem ate 2 KB e a saida ate 120 tokens. | inferred |

**Open questions:** none - os defaults estao decididos; o usuario aprovou executar a spec.

## User Stories

### P1: Controlar globalmente a geracao de resumos

**User Story**: Como operador da demonstracao, quero ligar ou desligar globalmente a feature de resumos para controlar quando o sistema pode gerar relatorios automaticamente.

**Acceptance Criteria**:

1. WHEN a aplicacao iniciar THEN o sistema SHALL manter a chave global `enabled=false` ate ativacao explicita.
2. WHEN `PUT /executive-summary/activation` receber `enabled=true` THEN o sistema SHALL ligar a chave global, persistir o instante `enabledAt` dessa janela de ativacao e nao criar relatorios imediatamente.
3. WHEN o mesmo PUT receber `enabled=false` THEN o sistema SHALL desligar a chave global e impedir novas geracoes automaticas; a repeticao do mesmo PUT SHALL manter o estado.
4. WHEN a chave global estiver ligada THEN o worker SHALL considerar apenas eventos cuja janela comercial comece em ou depois de `enabledAt`, com `endsAt` definido e atingido enquanto a chave permanecer ligada.
5. IF a chave global for ligada depois do inicio comercial de um evento THEN o worker SHALL excluir esse evento desta janela de ativacao, sem tentar recompor fatos anteriores nem chamar Bedrock.
6. WHILE a chave global estiver desligada, o worker SHALL nao gerar relatorios nem consultar CloudWatch ou Bedrock; o GET de um evento sem relatorio SHALL retornar `status=DISABLED` e `markdown=null`.
7. IF o PUT de ativacao apontar para corpo sem booleano `enabled` valido THEN o sistema SHALL retornar HTTP `400` sem alterar o estado global.

**Independent Test**: alternar a chave global com relogio controlado e provar que so eventos iniciados durante a janela atual de ativacao se tornam elegiveis, sem chamadas externas para eventos excluidos.

### P1: Gerar uma vez no encerramento

**User Story**: Como apresentador, quero que o resumo apareca apos o fim da janela comercial sem executar outro comando.

**Acceptance Criteria**:

1. WHEN o relogio do PostgreSQL atingir `endsAt` de um evento elegivel enquanto a chave global estiver ligada THEN a primeira varredura do worker apos esse instante SHALL aguardar reservas ja em transacao no inventario, reivindicar a geracao e publicar o relatorio salvo no Discord quando o webhook estiver configurado, sem esperar a expiracao das reservas ainda validas.
2. WHILE o relogio do PostgreSQL estiver antes de `endsAt` THEN o worker SHALL manter o evento em `SCHEDULED` e nao chamar servicos AWS para o resumo.
3. WHEN duas tasks do worker processarem o mesmo evento THEN o PostgreSQL SHALL permitir uma unica reivindicacao por `event_id`, no maximo uma tentativa Bedrock e no maximo uma tentativa de publicacao Discord.
4. WHEN a reivindicacao for gravada THEN o sistema SHALL persistir os fatos e o Markdown deterministico antes de qualquer chamada externa; IF o worker cair depois disso THEN o relatorio parcial SHALL permanecer consultavel e nenhuma nova varredura SHALL repetir a inferencia.
5. WHEN a publicacao Discord retornar sucesso THEN o sistema SHALL persistir `deliveryStatus=SENT` e o horario da confirmacao; IF o envio falhar ou tiver resultado ambiguo THEN SHALL persistir `deliveryStatus=FAILED` ou `UNKNOWN` sem repetir automaticamente.

**Independent Test**: controlar o relogio do banco e duas tasks concorrentes; verificar o limite de `endsAt`, uma reivindicacao, template persistido, uma unica tentativa Discord e ausencia de segunda chamada apos falha.

### P1: Explicar o resultado e o ritmo das reservas

**User Story**: Como pessoa nao tecnica, quero saber se a disponibilidade se esgotou, quanto tempo levou e como as reservas se distribuiram ao longo do evento.

**Acceptance Criteria**:

1. WHEN o relatorio for criado THEN o sistema SHALL exibir inicio (`startsAt`, ou `createdAt` quando nao houver inicio programado), fim (`endsAt`) e horario da apuracao em `America/Sao_Paulo`.
2. WHEN as reservas forem agregadas THEN o sistema SHALL mostrar a capacidade, `COUNT(*)` de reservas aceitas e `SUM(quantity)` de ingressos que passaram por reservas aceitas no evento.
3. WHEN o fechamento for apurado THEN o sistema SHALL mostrar a soma de ingressos em reservas ainda validas em `endsAt`, excluindo reservas canceladas ate esse instante e reservas cujo `expiresAt <= endsAt`.
4. WHEN o inventario chegar pela primeira vez a `available=0` durante a janela THEN o sistema SHALL registrar esse instante na mesma transacao do decremento e exibir o tempo desde o inicio comercial como "primeira vez sem ingressos disponiveis"; IF isso nao ocorrer THEN o relatorio SHALL dizer "a disponibilidade nao chegou a zero".
5. WHEN houver reservas aceitas THEN o sistema SHALL mostrar o minuto de maior quantidade de ingressos reservados.
6. WHERE a janela durar pelo menos 10 minutos e houver reservas aceitas, o sistema SHALL mostrar a parcela de ingressos reservados nos primeiros 5 minutos.
7. IF nenhuma reserva tiver sido aceita THEN o sistema SHALL mostrar zero e omitir indicadores de ritmo, sem inferir ausencia de interesse.
8. WHEN houver cancelamentos ou vencimentos ate `endsAt` THEN o sistema SHALL mostrar em uma linha as respectivas quantidades de ingressos, sem usar estados internos como `PENDING` ou `EXPIRED` no texto para o publico.
9. The report SHALL distinguir volume acumulado de reservas e reservas validas no fechamento, mesmo quando o primeiro superar a capacidade, e SHALL incluir a nota fixa "Reservas sao temporarias; compras concluidas nao sao verificadas aqui."

**Independent Test**: eventos concorrentes, quantidades maiores que um, primeira lotacao seguida de devolucao, nenhum esgotamento, janela curta, cancelamento antes/depois do fim e expiracao antes/depois do fim produzem numeros e frases exatos.

### P1: Oferecer leitura curta sem transformar alerta em manchete

**User Story**: Como apresentador, quero uma leitura executiva de ate duas frases e, quando relevante, uma nota sobre alertas observados no periodo.

**Acceptance Criteria**:

1. WHEN houver transicoes `ALARM` nos alarmes configurados durante `[inicio, endsAt]` THEN o relatorio SHALL mostrar no maximo duas notas em linguagem comum sob "Operacao", rotuladas como sinais do ambiente sem atribuir causa ao evento.
2. IF nao houver transicoes nos alarmes consultados THEN o Markdown SHALL omitir a secao de alertas; IF a consulta falhar ou for parcial THEN o Markdown SHALL dizer "Nao foi possivel verificar todos os alertas de infraestrutura."
3. WHEN os fatos comerciais estiverem persistidos THEN o sistema SHALL enviar ao Bedrock somente agregados permitidos, sem nome/e-mail de cliente, segredo, log bruto ou nomes tecnicos de alarmes, com entrada de ate 2 KB, saida de ate 120 tokens e uma unica tentativa por evento.
4. IF Bedrock falhar, truncar ou devolver texto com algarismos, compra confirmada, causalidade de incidente ou mais de duas frases THEN o sistema SHALL omitir a leitura por IA e manter intacto o template deterministico, sem retry automatico.
5. WHEN `GET /events/{id}/executive-summary` for chamado THEN o sistema SHALL retornar `200` com `status`, `markdown`, `generatedAt`, `asOf` e `deliveryStatus` sem executar agregacao, CloudWatch, Bedrock ou Discord; IF o evento nao existir THEN SHALL retornar `404`.

**Independent Test**: simular historico de alertas presente/vazio/indisponivel e respostas Bedrock validas/invalidas; repetir o GET e verificar que os fatos e o custo nao mudam.

### P1: Publicar o resumo no Discord

**User Story**: Como apresentador, quero receber no canal Discord o mesmo resumo executivo produzido no fechamento do evento.

**Acceptance Criteria**:

1. WHEN o relatorio deterministico estiver persistido e `discord_webhook_secret_arn` estiver configurado THEN o worker SHALL obter a URL do AWS Secrets Manager e enviar o texto final salvo ao Incoming Webhook uma unica vez.
2. WHEN o operador configurar o Discord THEN a documentacao SHALL orienta-lo a criar um Incoming Webhook nas configuracoes do canal, armazenar sua URL como `SecretString` no AWS Secrets Manager e informar somente o ARN em `infra/environments/demo/demo.tfvars` local.
3. WHEN a resposta do Discord confirmar o envio THEN o sistema SHALL persistir `deliveryStatus=SENT` e o horario da confirmacao.
4. IF o segredo nao estiver configurado THEN o sistema SHALL registrar `deliveryStatus=NOT_CONFIGURED`, nao consultar Secrets Manager e manter o relatorio consultavel.
5. IF Discord retornar erro definitivo THEN o sistema SHALL registrar `deliveryStatus=FAILED`; IF a chamada terminar sem resultado confirmado THEN SHALL registrar `deliveryStatus=UNKNOWN`; em ambos os casos nao SHALL repetir automaticamente o POST.
6. WHEN o conteudo do relatorio exceder 1.900 caracteres THEN o sistema SHALL encurtar a mensagem preservando resultado, primeiro esgotamento, ritmo e ressalva sobre reservas; toda mensagem SHALL ter no maximo 2.000 caracteres.

**Independent Test**: cliente HTTP Discord simulado cobre segredo ausente, envio aceito, erro, timeout, texto extenso e entrega unica para relatorio persistido.

## Edge Cases

- IF dois eventos compartilharem o mesmo alerta de infraestrutura THEN cada relatorio SHALL rotula-lo como sinal do ambiente, sem afirmar que um deles causou o alerta.
- IF a contagem de reservas por evento divergir dos agregados usados no relatorio THEN o sistema SHALL mostrar "Apuracao incompleta", omitir numeros inconsistentes, manter `status=PARTIAL` e nao chamar Bedrock.
- IF a chave global for desligada antes do fechamento de um evento THEN o sistema SHALL manter os dados transacionais, nao gerar resumo no fechamento e nao incluir esse evento em uma janela futura de ativacao.
- IF o PUT global de ativacao nao contiver um booleano `enabled` valido THEN o sistema SHALL retornar HTTP `400` sem alterar a flag.
- IF a URL do webhook nao estiver configurada THEN o worker SHALL persistir o relatorio com `deliveryStatus=NOT_CONFIGURED` sem falhar a apuracao ou consultar Secrets Manager.
- IF Discord responder com falha ou o resultado do POST for ambiguo THEN o worker SHALL preservar o relatorio e registrar estado de entrega sem repetir a mensagem automaticamente.
- IF nenhuma reserva tiver sido aceita ate `endsAt` THEN o sistema SHALL omitir a leitura por IA e nao chamar Bedrock, preservando o resultado deterministico.
- WHEN uma reserva for aceita durante concorrencia pelo inventario THEN o sistema SHALL usar o instante da decisao de aceite no PostgreSQL como `reservation.createdAt` e como base para os indicadores de ritmo e prazo da reserva, em vez do instante anterior a espera pelo inventario.

## Implicit-Requirement Dimensions

| Dimension | Resolution |
| --- | --- |
| Input validation & bounds | Ativacao global exige booleano; eventos precisam iniciar durante a janela ativa; prompt ate 2 KB, saida ate 120 tokens. |
| Failure / partial-failure states | Template comercial duravel antes de AWS; falha de IA ou monitoramento nao apaga fatos. |
| Idempotency / retry / duplicate handling | PUT global idempotente e PK unica do relatorio por evento; uma tentativa Bedrock e uma tentativa Discord, sem retry automatico. |
| Auth boundaries & rate limits | PUT global e GET passam pela borda IAM/SigV4; worker usa permissoes minimas para Bedrock, CloudWatch e o segredo Discord. |
| Concurrency / ordering | PostgreSQL decide mudanca do controle global, decremento, primeiro zero e reivindicacao com locks/condicoes atomicas. |
| Data lifecycle / expiry | Relatorio persiste enquanto evento existir; nenhuma PII no relatorio ou no prompt. |
| Observability | Registrar estado e consumo de tokens sem prompt/resposta brutos. |
| External-dependency failure | CloudWatch, Bedrock, Secrets Manager e Discord nao impedem persistencia ou consulta dos fatos comerciais. |
| State-transition integrity | Controle global `OFF -> ON(enabledAt) -> OFF`; relatorio por evento `SCHEDULED -> PARTIAL/READY`; eventos fora da janela de ativacao nao sao recuperados retroativamente. |

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| EXECSUM-01 | Controle global | Specify | Verified |
| EXECSUM-02 | Controle global | Specify | Verified |
| EXECSUM-03 | Controle global | Specify | Verified |
| EXECSUM-04 | Controle global | Specify | Verified |
| EXECSUM-05 | Controle global | Specify | Verified |
| EXECSUM-06 | Controle global | Specify | Verified |
| EXECSUM-07 | Controle global | Specify | Verified |
| EXECSUM-08 | Encerramento | Specify | Verified |
| EXECSUM-09 | Encerramento | Specify | Verified |
| EXECSUM-10 | Encerramento | Specify | Verified |
| EXECSUM-11 | Resultado e ritmo | Specify | Verified |
| EXECSUM-12 | Resultado e ritmo | Specify | Verified |
| EXECSUM-13 | Resultado e ritmo | Specify | Verified |
| EXECSUM-14 | Resultado e ritmo | Specify | Verified |
| EXECSUM-15 | Resultado e ritmo | Specify | Verified |
| EXECSUM-16 | Resultado e ritmo | Specify | Verified |
| EXECSUM-17 | Resultado e ritmo | Specify | Verified |
| EXECSUM-18 | Resultado e ritmo | Specify | Verified |
| EXECSUM-19 | Resultado e ritmo | Specify | Verified |
| EXECSUM-20 | Leitura e alertas | Specify | Verified |
| EXECSUM-21 | Leitura e alertas | Specify | Verified |
| EXECSUM-22 | Leitura e alertas | Specify | Verified |
| EXECSUM-23 | Leitura e alertas | Specify | Verified |
| EXECSUM-24 | Leitura e alertas | Specify | Verified |
| EXECSUM-25 | Edge cases | Specify | Verified |
| EXECSUM-26 | Edge cases | Specify | Verified |
| EXECSUM-27 | Edge cases | Specify | Verified |
| EXECSUM-28 | Edge cases | Specify | Verified |
| EXECSUM-29 | Edge cases | Specify | Verified |
| EXECSUM-30 | Edge cases | Specify | Verified |
| EXECSUM-31 | Edge cases | Specify | Verified |
| EXECSUM-32 | Edge cases | Specify | Verified |
| EXECSUM-33 | Edge cases | Specify | Verified |
| EXECSUM-34 | Edge cases | Specify | Verified |
| EXECSUM-35 | Encerramento | Specify | Verified |
| EXECSUM-36 | Leitura e alertas | Specify | Verified |
| EXECSUM-37 | Edge cases | Specify | Verified |
| EXECSUM-38 | Edge cases | Specify | Verified |
| EXECSUM-39 | Discord | Specify | Verified |
| EXECSUM-40 | Discord | Specify | Verified |
| EXECSUM-41 | Discord | Specify | Verified |
| EXECSUM-42 | Discord | Specify | Verified |
| EXECSUM-43 | Discord | Specify | Verified |
| EXECSUM-44 | Discord | Specify | Verified |

**Coverage:** 44/44 requisitos verificados independentemente em `validation.md`.

## Success Criteria

- [x] A flag global desligada impede relatorios e chamadas de IA/Discord; enquanto ligada, cada evento elegivel produz um unico relatorio e no maximo uma mensagem na primeira varredura apos `endsAt`.
- [x] O leitor identifica em menos de uma pagina o resultado, o tempo ate a primeira indisponibilidade total e o ritmo das reservas.
- [x] Numeros e marcos temporais sao derivados do PostgreSQL; IA e monitoramento podem falhar sem perder o resultado comercial.
- [x] Nenhuma compra concluida, receita, conversao ou causalidade de incidente e afirmada sem fonte de dados propria.
- [x] Cada relatorio elegivel gera no maximo uma mensagem no Discord, com estado de entrega consultavel e URL armazenada fora do repositorio.
