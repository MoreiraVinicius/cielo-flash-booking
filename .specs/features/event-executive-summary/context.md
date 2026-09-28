# Contexto do resumo executivo da performance do evento

**Spec:** `.specs/features/event-executive-summary/spec.md`
**Status:** Aprovado para implementacao pelo pedido do usuario

## Limite funcional

O produto desta feature e um relatorio curto por evento sobre capacidade, volume e ritmo das reservas durante a janela comercial. Uma chave global liga/desliga a feature para toda a aplicacao. Quando ligada desde o inicio da janela comercial, o fechamento em `endsAt` dispara apuracao, uma leitura curta do Bedrock e uma mensagem para o unico webhook Discord configurado. A chave global nao e uma flag por evento nem um comando para gerar relatorio manualmente.

## Decisoes do usuario

- A feature flag e global, nao por evento.
- O publico e nao tecnico; resultado comercial e ritmo de reservas devem dominar o relato. Alertas sao secundarios.
- O resumo acontece na conclusao do evento e responde se toda a disponibilidade se esgotou e quanto tempo levou.
- A geracao nao deve ocorrer sempre; a chave global sera ligada manualmente para a apresentacao.
- Amazon Bedrock e o provedor escolhido; prompt e modelo devem privilegiar custo baixo e poucas chamadas.
- O resumo deve ser enviado ao Discord do usuario, com a configuracao do canal descrita.
- O template e fixo. Numeros e datas vem do sistema; a IA escreve apenas uma leitura curta.
- Os dados sinteticos do seeder estao explicitos na apresentacao e nao alteram o escopo deste relatorio.

## Defaults de implementacao

- Um controle global persistido inicia desligado. `PUT /executive-summary/activation` altera a chave sem gerar relatorio imediatamente.
- Cada ativacao inicia uma janela global em `enabledAt`. Somente eventos cuja janela comercial comece durante essa ativacao e que terminem enquanto ela continua ligada sao elegiveis. Desligar fecha a janela; uma ativacao posterior nao recupera eventos antigos ou eventos que comecaram durante um periodo desligado.
- O resumo continua sendo criado automaticamente em `endsAt`. O worker salva primeiro os fatos; Bedrock e Discord recebem no maximo uma tentativa cada. GETs nao repetem nenhum efeito externo.
- A URL do Discord e um Incoming Webhook. O operador cria o webhook nas configuracoes do canal, armazena sua URL como `SecretString` em AWS Secrets Manager e configura somente o ARN no arquivo local ignorado `infra/environments/demo/demo.tfvars`, na variavel `discord_webhook_secret_arn`.
- Terraform nao cria nem guarda o valor secreto. O worker recebe IAM `secretsmanager:GetSecretValue` restrito ao ARN; a aplicacao busca o segredo apenas quando for enviar e mantem cache em memoria.
- Falha na entrega Discord nao apaga o relatorio. O GET mostra `SENT`, `FAILED`, `UNKNOWN` ou `NOT_CONFIGURED`.
- O texto publico usa "ingressos em reservas aceitas", "reservas validas no fechamento" e "primeira vez sem ingressos disponiveis". Nao usa "vendas concluidas" nem apresenta siglas de estado.
- Eventos sem reservas nao fazem chamada Bedrock. Alertas do ambiente aparecem apenas se houver transicoes observadas; ausencia de alertas nao declara ausencia de incidente.

## Dados presentes e lacunas

| Pergunta | Evidencia | Tratamento |
| --- | --- | --- |
| Quando abriu e fechou? | `event.starts_at`, `created_at`, `ends_at` | Inicio imediato usa `created_at`; `ends_at` e necessario para elegibilidade. |
| Quantas reservas e ingressos foram aceitos? | `reservation.event_id`, `quantity`, `created_at` | Fluxo acumulado, separado de capacidade e de posicao no fechamento. |
| Quantos ingressos estavam em reservas validas no fechamento? | `created_at`, `expires_at`, `status`, `updated_at` | Reconstruir validade em `endsAt`, independentemente do status posterior. |
| Quando a disponibilidade chegou a zero? | Hoje nao ha marco duravel; `available` pode subir depois. | Persistir o primeiro zero na transacao de inventario durante a janela global ativa. |
| Como foi o ritmo? | Horarios e quantidades das reservas aceitas. | Minuto de pico e concentracao nos primeiros 5 minutos quando a janela permitir. |
| Houve compra concluida, receita ou demanda rejeitada? | Nao ha esses fatos no contrato executavel. | Nao inferir. AD-031 e proposta separada de confirmacao externa. |
| Houve incidente causado pelo evento? | Alarmes sao do ambiente, sem correlacao causal. | Nota operacional secundaria, nunca causalidade. |

## Configuracao do Discord para a demonstracao

1. No Discord, abrir as configuracoes do canal de destino, ir a **Integracoes > Webhooks** e criar um Incoming Webhook.
2. Copiar a URL do webhook e criar no AWS Secrets Manager um segredo do tipo texto simples (`SecretString`) com essa URL como valor.
3. Copiar o ARN do segredo. Em `infra/environments/demo/demo.tfvars` local, definir `discord_webhook_secret_arn = "<ARN>"`. Esse arquivo e ignorado pelo Git; nunca colar a URL secreta no arquivo.
4. Aplicar Terraform para passar o ARN ao worker e conceder acesso ao segredo. O envio acontece no fechamento dos eventos elegiveis enquanto a chave global estiver ligada.
5. Ao terminar a apresentacao, desligar a chave global. Mensagens ja enviadas e relatorios persistidos permanecem disponiveis.

## Ideias adiadas

- Discord bot para receber comandos ou conversar com usuarios; um Incoming Webhook so envia mensagens.
- Incorporar compras confirmadas quando a proposta de confirmacao externa tiver contrato executavel.
- Medir rejeicoes por evento para falar de demanda nao atendida ou conversao.
