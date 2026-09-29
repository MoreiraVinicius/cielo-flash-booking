# Desenho do resumo executivo da performance do evento

**Spec:** `.specs/features/event-executive-summary/spec.md`
**Status:** Implementado e verificado independentemente; sem deploy ou chamadas externas reais

## Architecture Overview

Uma chave global persistida inicia desligada. O operador a liga pela API antes da janela comercial que deseja apresentar. O comando de reserva continua decidindo disponibilidade no PostgreSQL; quando a chave esta ligada e o inventario chega a zero, registra o primeiro instante no mesmo `UPDATE`. Depois de `endsAt`, o worker existente reivindica uma unica apuracao e salva fatos comerciais e Markdown deterministico. Bedrock escreve a leitura curta; o worker envia o texto salvo ao webhook Discord configurado. O GET consulta o relatorio e seu estado de entrega.

```text
PUT ativacao -> executive_summary_control(enabled, enabled_at)
                      |
POST reserva -> UPDATE inventario -> first_available_zero_at
                      |
event.ends_at -> worker -> fatos em endsAt + claim unico -> template salvo
                                                    -> CloudWatch opcional
                                                    -> Bedrock uma tentativa
                                                    -> URL do Secrets Manager
                                                    -> Discord uma tentativa
                                                    -> GET autenticado
```

A ativacao e global e persistida para que o operador possa liga-la/desliga-la sem redeploy e para manter o instante de inicio da janela ativa entre reinicios. Eventos cuja janela comercial comeca antes de `enabled_at`, ou que terminam com o controle desligado, nao geram relatorio retroativo.

`GET /events/{id}/executive-summary` e uma consulta sem efeitos externos que le o registro persistido e seu `deliveryStatus`. Sem registro, responde `DISABLED`, `NOT_ELIGIBLE` ou `SCHEDULED`; quando existe, devolve o status armazenado (`PARTIAL`/`READY`), Markdown, `generatedAt`, `asOf` e estado da entrega. Evento inexistente retorna `404`. A ativacao e essa consulta sao publicadas no API Gateway com `AWS_IAM`/SigV4 e encaminhadas pelo VPC Link ao ALB interno; PUT segue para command-api e GET para query-api.

## Code Reuse Analysis

| Component | Location | Use |
| --- | --- | --- |
| Janela comercial | `domain/event/Event.java`, Flyway V4 | `startsAt`, `endsAt` e inicio imediato. |
| Decremento atomico | `adapter/out/persistence/inventory/JdbcInventoryOperations.java` | Gravar primeiro zero na mesma atualizacao condicional. |
| Segredo runtime | `infra/modules/compute/main.tf` | Passar somente ARN do webhook ao worker e limitar `GetSecretValue`. |
| Worker agendado | `reservation/reconciliation/ExpirationReconciler.java` | Reutilizar padrao `@Scheduled`, sem compartilhar transacao. |
| JDBC | `adapter/out/persistence/reservation/JdbcReservationPersistenceAdapter.java` | Agregacao por `event_id` e horario PostgreSQL. |
| Borda e IAM | AD-014, `infra/modules/edge-observability/main.tf` | Publicar PUT e GET com SigV4, WAF e throttling existentes. |

## Components

| Component | Responsibility | Contract |
| --- | --- | --- |
| `EventSummaryActivation` | Ligar/desligar um controle global e registrar o inicio da janela ativa. | `set(enabled)`; repeticoes no mesmo estado sao idempotentes e nao reiniciam `enabledAt`. |
| `JdbcInventoryOperations` | Registrar o primeiro zero somente para evento ativado. | Decremento condicional, timestamp do PostgreSQL na mesma transacao. |
| `EventSummaryFactsReader` | Agregar volume, validade em `endsAt`, ritmo e invariantes. | `read(eventId, endsAt)` sem PII e sem depender do status observado depois. |
| `ExecutiveSummaryRenderer` | Montar o texto curto com numeros fixos. | `render(facts, signals, narrative)`; omite secao de IA/alertas quando nao houver dados. |
| `EventSummaryScheduler` | Varredura limitada, claim duravel e orquestracao fora de transacao. | `scan()` no perfil worker; lote default 25 (limite 100), intervalo default 30 s; uma linha por `event_id`. |
| `ExecutiveSummaryDeliveryService` | Fazer uma tentativa de publicacao depois de salvar o Markdown final. | Persiste `UNKNOWN` antes de invocar o publisher; ARN ausente nao consulta AWS. |
| `SecretsManagerDiscordSummaryPublisher` | Ler/cachear URL do segredo e fazer POST sem retry/redirect. | Aceita apenas URL HTTPS do endpoint Incoming Webhook Discord e retorna `SENT/FAILED/UNKNOWN`. |
| `DiscordSummaryMessageFormatter` | Respeitar o limite de caracteres do Discord. | Remove leitura/operacao opcionais antes de encurtar; preserva resultado, ritmo e ressalva. |
| `OperationalSignalsReader` | Observar ate duas transicoes relevantes de alarmes do ambiente. | `read(start, endsAt)` retorna `OBSERVED/EMPTY/UNAVAILABLE/PARTIAL` e rotulos comuns. |
| `ExecutiveNarrative` | Redigir ate duas frases sobre fatos comerciais. | `write(facts)` retorna texto validado ou ausencia; uma tentativa. |
| `DiscordSummaryPublisher` | Enviar o Markdown ja salvo a um canal Discord. | Uma requisicao POST por relatorio; resultado `SENT/FAILED/UNKNOWN/NOT_CONFIGURED`. |
| `EventSummaryQueryController` | Ler estado, Markdown e entrega persistidos. | `GET /events/{id}/executive-summary`, sem trabalho externo. |
| `JdbcExecutiveSummaryReportReader` | Resolver estado atual e carregar relatorio existente. | Uma leitura PostgreSQL; `DISABLED/NOT_ELIGIBLE/SCHEDULED` sem report e `PARTIAL/READY` com snapshot salvo. |

## Data Models

Uma nova migration Flyway cria `executive_summary_control` como singleton com `enabled BOOLEAN NOT NULL DEFAULT FALSE` e `enabled_at TIMESTAMPTZ NULL`, e acrescenta `first_available_zero_at TIMESTAMPTZ NULL` a `event`. Ao alternar de desligado para ligado, a transacao grava `clock_timestamp()` como `enabled_at`; PUT repetido no mesmo estado nao reinicia o periodo. Inventario grava primeiro zero somente quando o singleton esta ligado. O mesmo instante PostgreSQL decide a janela comercial, alimenta o marco e retorna como horario de aceite; `reservation.created_at` e o prazo passam a partir desse instante. O claim do relatorio referencia cada `event_id` numa tabela separada, sem flag por evento.

`event_executive_summary` tem `event_id UUID PRIMARY KEY REFERENCES event(id) ON DELETE RESTRICT`, `status TEXT NOT NULL CHECK (status IN ('PARTIAL','READY'))`, `delivery_status TEXT NOT NULL CHECK (delivery_status IN ('NOT_CONFIGURED','SENT','FAILED','UNKNOWN'))`, `as_of TIMESTAMPTZ NOT NULL`, `generated_at TIMESTAMPTZ NOT NULL`, `delivery_confirmed_at TIMESTAMPTZ NULL`, `markdown TEXT NOT NULL` e colunas numericas verificaveis para reservas aceitas, ingressos acumulados, ingressos validos em `endsAt`, cancelados/vencidos ate o fim, minuto de pico e primeiros cinco minutos. Guarda tambem `first_available_zero_at`, `model_id`, `input_tokens`, `output_tokens` e `error_code` opcionais. A PK fornece idempotencia; o estado `UNKNOWN` e persistido antes do POST, assim uma queda antes ou depois do envio nunca inicia uma segunda tentativa automaticamente. Nenhuma PII, webhook URL ou prompt bruto e persistido. O indice existente com `event_id` atende a selecao de reservas; so adicionar indice temporal apos `EXPLAIN` demonstrar necessidade. A varredura de eventos usa lote limitado.

`executive_summary_control` tem exatamente uma linha (`id BOOLEAN PRIMARY KEY DEFAULT TRUE CHECK (id)`, `enabled BOOLEAN NOT NULL DEFAULT FALSE`, `enabled_at TIMESTAMPTZ`). A API de ativacao insere/atualiza essa linha. A elegibilidade compara `COALESCE(event.starts_at, event.created_at) >= enabled_at`, exige `ends_at <= clock_timestamp()` e flag global ainda ligada. A desativacao impede novos claims. O runbook mantem a flag ligada ate a publicacao terminar.

### Apuracao no fechamento

O relatorio usa `endsAt` como corte comercial e o horario da leitura como `asOf`. O universo aceito inclui somente reservas do evento com `saleStartsAt <= created_at < endsAt`; volume acumulado e `COUNT(*)` e `SUM(quantity)` desse universo. Ingressos validos em `endsAt` exigem `expires_at > endsAt` e reserva ainda pendente no fechamento (`status=PENDING` ou `updated_at > endsAt`), permitindo reconstruir uma reserva que so foi cancelada/vencida depois do fim. Cancelamentos ate o fim usam `status=CANCELLED` e `updated_at <= endsAt`. Vencimentos ate o fim usam `expires_at <= endsAt`, exceto quando uma reserva foi cancelada antes do fim. Essas tres quantidades formam uma particao do volume aceito; se nao fecharem exatamente, o conjunto de fatos e marcado incompleto, os numeros inconsistentes sao omitidos do texto e Bedrock nao e consultado. Os agregados de quantidade sao `BIGINT`, embora a capacidade seja `INTEGER`.

O ritmo vem de `created_at` e `quantity`: pico = maior soma de ingressos em um minuto da janela, com desempate pelo minuto mais cedo; participacao inicial = ingressos aceitos nos primeiros 5 minutos / total aceito, arredondada ao inteiro mais proximo, exibida apenas se a janela durar pelo menos 10 minutos e o total for positivo. O tempo ate o primeiro zero e `first_available_zero_at - inicio comercial`, arredondado em segundos e formatado em minutos/segundos. O zero de disponibilidade e um fato de inventario, nao prova de compra ou de reserva ainda valida no fechamento.

## Template publico

```markdown
# {nome do evento}
Inicio: {inicio} | Fim: {fim} | Apurado: {asOf} | Capacidade: {capacity} ingressos

## Resultado
{accepted_tickets} ingressos passaram por {accepted_reservations} reservas aceitas.
{valid_tickets_at_close} ingressos ainda tinham reserva valida no fechamento.
{primeiro zero: "A disponibilidade chegou a zero apos {duracao}." | "A disponibilidade nao chegou a zero."}

## Ritmo
{pico de {peak_tickets} ingressos em um minuto | "Nenhuma reserva aceita."}
{parcela nos primeiros 5 minutos, somente se janela >= 10 minutos}

## Leitura
{ate duas frases da IA; secao omitida se indisponivel}

## Operacao
Sinais observados no ambiente; nao ha confirmacao de relacao com este evento.
{ate duas notas de alerta em linguagem comum; secao omitida se nao houver transicoes}
{nota discreta apenas se monitoramento parcial/indisponivel}

Reservas sao temporarias; compras concluidas nao sao verificadas aqui.
```

Cancelamentos e vencimentos ate o fim entram em uma unica frase de Resultado somente quando positivos. O texto nao mostra `PENDING`, `EXPIRED`, `ALARM`, nome tecnico de servico, tokens ou termos de arquitetura. Datas e duracoes sao formatadas em pt-BR; nenhum paragrafo repete os mesmos numeros apenas para preencher espaco.

Exemplo de densidade e tom (numeros apenas ilustrativos):

```markdown
# Evento X
12h00 a 12h30 | Capacidade: 1.000 ingressos

## Resultado
1.040 ingressos passaram por 1.012 reservas aceitas.
960 ingressos ainda tinham reserva valida no fechamento.
A disponibilidade chegou a zero pela primeira vez apos 8 minutos.
80 ingressos tiveram reservas canceladas ou vencidas ate o fechamento.

## Ritmo
O pico foi de 210 ingressos reservados em um minuto.
62% dos ingressos foram reservados nos primeiros 5 minutos.

## Leitura
As reservas se concentraram no inicio e a disponibilidade se esgotou temporariamente. No fechamento, parte da capacidade ja nao estava em reservas validas.

Reservas sao temporarias; compras concluidas nao sao verificadas aqui.
```

## Trigger, flag e estados

`DISABLED` e calculado quando a chave global esta desligada e nao ha relatorio; `NOT_ELIGIBLE` indica que a janela comercial comecou antes de `enabled_at`; `SCHEDULED` significa janela elegivel ainda aberta ou aguardando a proxima varredura. `GET /events/{id}/executive-summary` usa uma unica leitura PostgreSQL direta para resolver a existencia do evento, o estado global e o snapshot salvo; nao usa cache, nao agrega fatos, nao chama AWS e nao altera estado. Se nao existe relatorio, devolve `DISABLED`, `NOT_ELIGIBLE` ou `SCHEDULED` com Markdown/datas/entrega nulos; se existe, devolve os campos persistidos (`PARTIAL/READY`, Markdown, datas e `deliveryStatus`). Evento inexistente retorna `404`. O endpoint de ativacao e `PUT /executive-summary/activation`; ambos sao publicados via API Gateway com `AWS_IAM`/SigV4 e VPC Link ao ALB privado.

O scheduler roda apenas no perfil `worker`/`all`, com intervalo default de 30 segundos e lote default de 25 eventos (configuravel ate 100). Primeiro seleciona candidatos fechados com a chave global ligada; para cada candidato, uma transacao curta bloqueia o controle global com `FOR SHARE`, bloqueia o evento com `FOR UPDATE`, revalida elegibilidade/fechamento, agrega os fatos e insere `PARTIAL` com Markdown deterministico. Isso aguarda commits de reservas que ja obtiveram o lock do inventario; tentativas novas apos `endsAt` sao recusadas. A chave unica por evento permite um vencedor entre workers concorrentes. A transacao termina antes de CloudWatch ou Bedrock. Se os fatos forem inconsistentes, nenhuma chamada externa e feita. Caso contrario, CloudWatch e (havendo reservas aceitas) Bedrock sao consultados no maximo uma vez. O sistema atualiza o mesmo Markdown e marca `READY` somente com fatos completos, cobertura de monitoramento completa e leitura valida quando aplicavel; falha, cobertura parcial/ausente ou queda apos o claim deixa o Markdown basico em `PARTIAL`. Uma linha ja reivindicada nunca volta a ser candidata, portanto nova varredura/GET nao repete custo. `deliveryStatus` e metadado separado, nunca faz parte do Markdown: o relatorio e identico antes e depois da confirmacao do Discord. Sem ARN configurado, a etapa de entrega registra `NOT_CONFIGURED` sem consultar Secrets Manager. Antes do unico POST, o worker persiste `UNKNOWN`; confirmacao vira `SENT`, erro definitivo vira `FAILED`, e timeout ou queda ambigua permanece `UNKNOWN`. Nenhum desses estados inicia novo POST automaticamente. O operador mantem a flag ligada ate a publicacao terminar e entao a desliga.

## AWS, custo e privacidade

Somente eventos elegiveis durante a chave global ligada consultam CloudWatch e, quando houver ao menos uma reserva aceita, chamam Bedrock uma vez. Discord recebe uma mensagem por relatorio, sem nova inferencia; GETs repetidos nao chamam AWS nem Discord. Evento sem reservas pula Bedrock. O model id e a regiao usam `executive-summary.bedrock.model-id` e `executive-summary.bedrock.region`, sobrescritiveis por `EXECUTIVE_SUMMARY_MODEL_ID` e `AWS_REGION`; a chamada tem timeout configuravel ate 60 segundos e retry SDK desativado. A lista `executive-summary.operational-signals.alarms` aceita ate 12 pares de nome CloudWatch e rotulo legivel. Cada historico pede somente transicoes de estado no intervalo, limita a 10 itens por alarme e nao le o resumo da AWS como texto publico. Historico sem transicoes omite Operacao; configuracao ausente, truncamento ou falha indicam verificacao indisponivel/parcial. Quando houver transicoes, rotulos e timestamps de no maximo duas sao precedidos pela ressalva "Sinais observados no ambiente; nao ha confirmacao de relacao com este evento." Alertas nao sao enviados ao modelo.

O operador cria um Incoming Webhook nas configuracoes do canal Discord e copia a URL. No AWS Console, cria um segredo de texto (`SecretString`) em Secrets Manager com essa URL. Em `infra/environments/demo/demo.tfvars`, define apenas `discord_webhook_secret_arn` com o ARN (o exemplo versionado deixa-o vazio); esse arquivo e ignorado pelo Git. Terraform passa somente o ARN como `EXECUTIVE_SUMMARY_DISCORD_WEBHOOK_SECRET_ARN` ao worker e limita `secretsmanager:GetSecretValue` ao ARN exato. Sem ARN, o worker nao consulta Secrets Manager. Com ARN, a transacao marca `UNKNOWN` e devolve o Markdown salvo antes de qualquer leitura do segredo ou POST. O worker le/cacheia o segredo na primeira publicacao; a URL nao aparece na task definition, variaveis de ambiente ou logs. O cliente desativa redirect e retry e usa timeout limitado; resposta HTTP 2xx vira `SENT`, erro HTTP definitivo `FAILED`, e timeout/queda ambigua `UNKNOWN`. A mensagem e limitada a 2.000 caracteres; acima de 1.900, remove primeiro leitura da IA e operacao, mantendo fatos, ritmo e ressalva.

Usar Amazon Nova Micro (`amazon.nova-micro-v1:0`) por default em `sa-east-1`, com `modelId` sobrescrevivel por configuracao. Nao provisionar throughput nem agente. A AWS lista Nova Micro como modelo textual de baixo custo para sumarizacao e suporta inferencia in-region em Sao Paulo. Prompt em pt-BR com campos permitidos, ate 2 KB, sem nome de evento, cliente, e-mail, log ou alarme. `maxTokens=120`, temperatura baixa e cliente sem retry SDK. A validacao da resposta rejeita algarismos e afirmacoes de compra/causalidade; texto rejeitado simplesmente nao aparece. A mensagem enviada fica abaixo de 2.000 caracteres. Cada evento elegivel com reservas tem no maximo uma inferencia, com limite de entrada e saida; nao ha custo fixo de throughput. O runbook remete a tabela atual de precos AWS, que varia por regiao e modelo.

Referencias AWS: [Amazon Nova Micro](https://docs.aws.amazon.com/bedrock/latest/userguide/model-card-amazon-nova-micro.html), [disponibilidade regional dos modelos](https://docs.aws.amazon.com/bedrock/latest/userguide/models-region-compatibility.html) e [precos do Amazon Bedrock](https://aws.amazon.com/bedrock/pricing/).

## Error Handling Strategy

| Scenario | Handling | Reader sees |
| --- | --- | --- |
| Flag desligada | Nenhuma apuracao ou chamada externa | `DISABLED`, sem Markdown. |
| Evento com janela iniciada antes da ativacao global | Excluir da janela ativa | `NOT_ELIGIBLE`, sem Markdown. |
| Corpo de ativacao global invalido | `400`, sem alterar estado | Erro HTTP objetivo no comando. |
| Evento elegivel ainda aberto | Nenhuma chamada externa | `SCHEDULED`, sem Markdown. |
| Evento fechado, worker ainda nao varreu | Aguarda a proxima varredura | `SCHEDULED`, sem Markdown. |
| Nenhuma reserva | Agregados zero | Resultado simples; nenhum julgamento de demanda. |
| Nenhum alerta observado | Omitir secao de Operacao | Nenhuma afirmacao sobre ausencia de incidente. |
| CloudWatch parcial/indisponivel | Prosseguir | Nota curta sobre verificacao incompleta. |
| Bedrock falha/resposta invalida | Sem retry | Resultado e ritmo intactos, sem secao Leitura. |
| Segredo Discord ausente | Sem leitura Secrets Manager | Relatorio salvo com `NOT_CONFIGURED`. |
| Discord erro/timeout | Marcar `FAILED`/`UNKNOWN`; sem retry | Relatorio acessivel, entrega nao confirmada. |
| Worker cai depois do claim | Nao repetir chamada | `PARTIAL` com Markdown comercial. |

## Risks & Concerns

| Concern | Location | Impact | Mitigation |
| --- | --- | --- | --- |
| `available` pode voltar a subir | `JdbcInventoryOperations.java:23` | Nao se descobre o primeiro zero pelo valor atual. | Gravar marco na mesma atualizacao condicional. |
| Horario atual e obtido antes da disputa pelo inventario | `CreateReservationService.java:36` | Pico e prazo podem ser adiantados sob concorrencia. | Retornar do decremento o horario de aceite PostgreSQL e persisti-lo em `reservation.created_at`. |
| Reserva expira apos `endsAt` | `application.yml:41` | Status lido depois nao representa o fechamento. | Reconstruir validade no instante `endsAt` com timestamps persistidos. |
| Ligar globalmente no meio de uma janela | Controle `enabled_at` | Indicadores de ritmo e primeiro zero seriam incompletos. | Excluir eventos cujo inicio comercial preceda a ativacao global. |
| URL do webhook permite publicar no Discord | AWS Secrets Manager | Vazamento permite publicar no canal. | Segredo fora do repositorio/tfvars; role worker restrita ao ARN; URL nao logada. |
| Timeout apos POST | `DiscordSummaryPublisher` | Mensagem pode ter chegado sem confirmacao. | Gravar `UNKNOWN` antes da chamada; nao repetir estado ambiguo. |
| Alarmes cobrem o ambiente | `infra/modules/edge-observability/main.tf` | Atribuicao causal enganosa. | Secao secundaria, nomes amigaveis e qualificacao de ambiente. |
| GET de evento usa cache | AD-010 | Flag poderia parecer defasada no GET comum. | Resposta da ativacao e GET do relatorio usam PostgreSQL; nao depender do cache para estado da flag. |
| Resultado remoto ambiguo | Nova integracao Bedrock | Retry pode duplicar custo. | Claim duravel antes de AWS e nenhuma segunda tentativa automatica. |

## Tech Decisions

| Decision | Choice | Rationale |
| --- | --- | --- |
| Feature flag | Uma chave global persistida, default off, sem ativacao por evento | Ativacao manual durante a apresentacao; nenhum custo de inferencia quando desligada. |
| Momento da apuracao | Primeira varredura apos `endsAt` | Relatorio de conclusao, sem aguardar expiracao de reservas. |
| Fonte de numeros | PostgreSQL por `event_id` e corte em `endsAt` | Separar volume aceito de reservas validas no fechamento. |
| Redacao | Template deterministico + ate duas frases comerciais | Precisao e custo baixo para publico nao tecnico; alertas sao descritos como sinais do ambiente sem relacao causal confirmada com o evento. |
| Entrega | GET autenticado e um Incoming Webhook Discord | Publicar o texto salvo sem nova inferencia. |
| Segredo Discord | AWS Secrets Manager; ARN apenas no `demo.tfvars` local | A URL e credencial de publicacao e nao deve aparecer no estado/codigo Terraform. |

Estas escolhas descrevem o comportamento implementado para a feature; execucao local e commits nao incluem deploy ou chamadas AWS/Discord reais. As decisoes de arquitetura ficam registradas em `.specs/STATE.md` sem modificar AD-007 nem sobrescrever decisoes anteriores.
