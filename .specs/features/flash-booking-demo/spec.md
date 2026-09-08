# Especificação da demo Flash Booking

## Problema

Construir o núcleo funcional de uma reserva de ingressos para flash sale. A solução deve impedir oversell, suportar múltiplas instâncias, expirar reservas e ser simples de operar por uma pessoa.

## Objetivos

- [ ] Entregar os cinco endpoints do case com comportamento verificável.
- [ ] Garantir consistência dos comandos sob concorrência.
- [ ] Executar localmente por Docker Compose.
- [ ] Provisionar todo o runtime AWS por Terraform.

## Fora do escopo

| Item | Motivo |
| --- | --- |
| Pagamento | Não consta no case. |
| Cadastro, senha e login de cliente final | Não constam no case; a borda autentica apenas operadores e entrevistadores. |
| Frontend | O desafio é backend. |
| Pipeline de CI/CD | Não será avaliada nesta entrega. |
| Alta disponibilidade | Pertence ao plano de alta carga. |
| EKS | Exige carga operacional incompatível com a demo individual. |

## Premissas e decisões

| Tema | Decisão | Justificativa | Confirmada? |
| --- | --- | --- | --- |
| Duração da reserva | 10 minutos configuráveis | Compatível com SQS message timer e comum para checkout. | yes |
| Aceitação da reserva | Síncrona, com estado PENDING | Bloqueio temporário, sem compra definitiva; ADR 0002. | yes |
| Liberação após vencimento | Até expiresAt + 5 segundos com banco e processamento saudáveis | Limita estoque temporariamente bloqueado; ADR 0003. | yes |
| Encerramento | Persistir código e descrição do motivo em CANCELLED e EXPIRED | O catálogo e o formato de consulta estão definidos no ADR 0006. | yes |
| Banco | RDS PostgreSQL 16 Single-AZ | Econômico e suficiente para a demo. | yes |
| Cache | ElastiCache for Valkey compartilhado | Cacheia os dois GETs por no máximo um segundo; ADR 0005. | yes |
| Autenticação da API | API Gateway REST com IAM/SigV4, principals e CIDRs permitidos | Autentica antes dos containers e evita senha compartilhada; ADR 0012. | yes |
| Serviços HTTP | `query-api` e `command-api` separados, usando a mesma imagem | Permite escala independente sem duplicar domínio; ADR 0013. | yes |
| Cliente da reserva | `Customer` ligado por chave estrangeira | Permite consulta e notificação sem criar endpoint de cadastro; ADR 0010. | yes |
| Notificação | E-mail assíncrono por outbox, SQS e SES | Não bloqueia a transação nem representa compra confirmada; ADR 0011. | yes |
| Região | `sa-east-1` | Proximidade com o contexto brasileiro da vaga. | yes |
| Build | Maven | Convenção simples para Spring Boot. | yes |

**Questões abertas:** nenhuma. O envelope de carga é medido na tarefa de benchmark e não é pré-requisito para implementar a demo; os limites de recuperação remota pertencem à arquitetura-alvo de alta carga.

## Histórias de usuário

### P1: Gerenciar eventos

**História:** Como operador, quero criar um evento e consultar sua disponibilidade para controlar a venda.

**Critérios de aceite:**

1. QUANDO `POST /events` receber nome e capacidade positiva, ENTÃO o sistema DEVE criar o evento e retornar `201`.
2. SE a criação receber dados inválidos, ENTÃO o sistema DEVE retornar `400` em `application/problem+json`.
3. QUANDO `GET /events/{id}` encontrar o evento, ENTÃO o sistema DEVE retornar capacidade total e disponível.
4. SE o evento não existir, ENTÃO o sistema DEVE retornar `404`.
5. QUANDO `GET /events/{id}` encontrar uma entrada válida no cache, ENTÃO o sistema DEVE retornar a disponibilidade exibida sem consultar PostgreSQL; em falta de cache, DEVE consultar PostgreSQL e preencher o cache por no máximo um segundo.
6. SE `POST /events/{id}/reservations` referenciar um evento inexistente, ENTÃO o sistema DEVE retornar `404` sem persistir cliente, reserva ou evento de outbox, e DEVE registrar essa resposta final no contrato de idempotência.

**Teste independente:** Criar um evento e consultá-lo pelo identificador retornado.

### P1: Reservar sem oversell

**História:** Como cliente, quero reservar ingressos sem que a capacidade seja ultrapassada e manter a reserva ligada aos meus dados.

**Critérios de aceite:**

1. QUANDO houver capacidade suficiente e cliente válido, ENTÃO o sistema DEVE criar ou reutilizar o cliente, decrementar a disponibilidade e criar uma reserva `PENDING` ligada ao cliente e ao evento na mesma transação.
2. SE não houver capacidade suficiente, ENTÃO o sistema DEVE retornar `409` sem alterar inventário, cliente ou reserva.
3. ENQUANTO múltiplas instâncias concorrerem pelo mesmo evento, o sistema DEVE manter a disponibilidade entre zero e a capacidade total.
4. QUANDO `GET /reservations/{id}` encontrar a reserva, ENTÃO o sistema DEVE retornar estado, quantidade, expiração, evento, cliente e motivo de encerramento.
5. QUANDO `GET /reservations/{id}` encontrar uma entrada válida no cache, ENTÃO o sistema DEVE retornar a reserva sem consultar PostgreSQL; em falta de cache, DEVE consultar PostgreSQL e preencher o cache por no máximo um segundo.
6. SE nome ou e-mail do cliente forem inválidos, ENTÃO o sistema DEVE retornar `400` sem bloquear capacidade.

**Teste independente:** Disparar reservas concorrentes acima da capacidade por ao menos dois processos de comandos e comprovar que a soma aceita não ultrapassa a capacidade.

### P1: Cancelar e expirar

**História:** Como cliente, quero cancelar uma reserva e ter reservas abandonadas expiradas automaticamente.

**Critérios de aceite:**

1. QUANDO uma reserva `PENDING` for cancelada, ENTÃO o sistema DEVE transicioná-la para `CANCELLED` e devolver capacidade uma vez.
2. QUANDO uma reserva `PENDING` atingir `expiresAt`, com banco e processamento de expiração saudáveis, ENTÃO o sistema DEVE concluir sua transição para `EXPIRED` e devolver capacidade uma vez até `expiresAt + 5 segundos`.
3. SE uma mensagem de expiração for processada novamente, ENTÃO o sistema DEVE manter inventário e estado inalterados.
4. SE o processamento assíncrono falhar, ENTÃO o sistema DEVE aplicar tentativas limitadas e encaminhar a falha esgotada para DLQ.
5. QUANDO uma reserva transicionar para `CANCELLED` ou `EXPIRED`, ENTÃO o sistema DEVE persistir código e descrição do motivo de encerramento na mesma transação do estado e da devolução de capacidade.
6. ENQUANTO o instante atual for anterior a `expiresAt`, o sistema DEVE impedir expiração antecipada da reserva.
7. QUANDO cancelamento ou expiração confirmar a transação, ENTÃO o sistema DEVE invalidar as entradas de cache da reserva e do evento afetado.
8. QUANDO `GET /reservations/{id}` retornar uma reserva terminal, ENTÃO o sistema DEVE retornar o objeto `closureReason` com `code` e `description`; para `PENDING`, esse objeto DEVE ser nulo.

**Teste independente:** Cancelar e expirar reservas com duplicidade de mensagens e conferir o inventário final.

### P1: Idempotência e erros

**História:** Como integrador, quero repetir requisições com segurança e receber erros explícitos.

**Critérios de aceite:**

1. QUANDO uma operação mutável repetir a mesma `Idempotency-Key` e o mesmo payload, ENTÃO o sistema DEVE retornar o resultado original sem novo efeito.
2. SE uma `Idempotency-Key` for reutilizada com outro payload, ENTÃO o sistema DEVE retornar `409`.
3. QUANDO uma resposta for produzida, ENTÃO o sistema DEVE incluir um identificador de correlação.
4. SE ocorrer erro inesperado, ENTÃO o sistema DEVE retornar `500` sem expor detalhes internos.
5. QUANDO um comando aceitar uma `Idempotency-Key`, ENTÃO o sistema DEVE persistir chave, operação, alvo normalizado, hash do payload e resposta final por 24 horas; repetição idêntica DEVE retornar a mesma resposta e reutilização incompatível DEVE retornar `409`.
6. SE um endpoint mutável não receber `Idempotency-Key`, ENTÃO o sistema DEVE retornar `400` em `application/problem+json` sem executar o comando.

**Teste independente:** Repetir requisições iguais e conflitantes, inclusive em paralelo.

### P1: Notificar a reserva

**História:** Como cliente, quero receber por e-mail os dados da reserva para conseguir consultá-la antes da expiração.

**Critérios de aceite:**

1. QUANDO uma reserva for confirmada como `PENDING`, ENTÃO o sistema DEVE gravar `ReservationCreated` no outbox na mesma transação.
2. QUANDO o worker processar `ReservationCreated`, ENTÃO o sistema DEVE enviar ao e-mail do cliente o identificador da reserva, evento, quantidade e `expiresAt`.
3. O e-mail DEVE declarar que se trata de reserva temporária e que não confirma compra ou pagamento.
4. SE o SES estiver indisponível, ENTÃO o sistema DEVE preservar a reserva, tentar novamente de forma limitada e encaminhar a mensagem esgotada para a DLQ de notificação.
5. QUANDO banco, fila, worker e SES estiverem saudáveis, ENTÃO o envio DEVE ser solicitado ao SES até 30 segundos após o commit da reserva.

**Teste independente:** Criar uma reserva no Docker Compose, localizar o e-mail no Mailpit e conferir dados e aviso de reserva temporária.

### P1: Proteger a API e limitar abuso

**História:** Como operador, quero que apenas identidades autorizadas usem a API e que rajadas sejam bloqueadas antes dos containers.

**Critérios de aceite:**

1. QUANDO uma requisição AWS não possuir assinatura SigV4 válida e permissão `execute-api:Invoke`, ENTÃO o API Gateway DEVE retornar `403` sem alcançar o VPC Link.
2. QUANDO a origem estiver fora dos CIDRs permitidos, ENTÃO a resource policy ou o WAF DEVE bloquear a chamada antes do ALB.
3. QUANDO uma rota GET ultrapassar 20 requisições por segundo ou burst 40 na demo, ENTÃO o API Gateway DEVE iniciar throttling e retornar `429`.
4. QUANDO uma rota POST ou DELETE ultrapassar 5 requisições por segundo ou burst 10 na demo, ENTÃO o API Gateway DEVE iniciar throttling e retornar `429`.
5. O API Gateway REST DEVE ser o único recurso público; ALB, ECS, PostgreSQL, Valkey e SQS DEVEM permanecer privados.
6. QUANDO o gasto atingir 50%, 80% ou 100% do orçamento de US$100, ENTÃO o AWS Budget DEVE emitir alerta; o documento operacional DEVE informar que o alerta não garante bloqueio imediato de cobrança.

**Teste independente:** Assinar uma chamada com role permitida, repetir sem assinatura e fora do CIDR, e validar por Terraform que nenhum backend possui entrada pública.

### P1: Executar e provisionar

**História:** Como avaliador, quero reproduzir a solução localmente e revisar sua arquitetura AWS.

**Critérios de aceite:**

1. QUANDO `docker compose up` concluir, ENTÃO o sistema DEVE disponibilizar `query-api`, `command-api`, worker, PostgreSQL, Valkey, mensageria e Mailpit locais.
2. QUANDO o perfil local de concorrência iniciar, ENTÃO ele DEVE executar ao menos dois processos de `command-api` contra o mesmo PostgreSQL.
3. QUANDO `terraform validate` executar, ENTÃO a infraestrutura DEVE ser válida.
4. QUANDO o plano Terraform da demo for aplicado com credenciais autorizadas, ENTÃO o sistema DEVE criar todos os recursos de runtime na AWS.
5. O sistema DEVE exigir zero criação manual de recursos pelo console AWS.
6. QUANDO a demonstração completar 1h30, ENTÃO o runbook DEVE orientar `terraform destroy` e a verificação dos recursos remanescentes.

**Teste independente:** Executar os gates locais, o cenário multiprocesso e gerar um `terraform plan` completo.

## Casos-limite

- SE a quantidade for zero ou negativa, ENTÃO o sistema DEVE retornar `400`.
- SE uma reserva referenciar evento inexistente, ENTÃO o sistema DEVE retornar `404` sem efeito parcial.
- SE cancelamento e expiração concorrerem, ENTÃO o sistema DEVE liberar capacidade uma vez.
- SE a publicação no SQS atrasar, ENTÃO o reconciliador DEVE expirar a reserva pelo horário persistido.
- SE o banco estiver indisponível, ENTÃO o sistema DEVE falhar sem confirmar reserva.
- SE o cache falhar, ENTÃO o sistema DEVE consultar PostgreSQL com timeout de cache de 100 ms, no máximo 5 fallbacks simultâneos por task e circuito aberto após 5 falhas em 10 segundos.
- SE o SQS entregar uma mensagem duplicada, ENTÃO o consumidor DEVE produzir o mesmo estado final.
- SE o envio de e-mail for duplicado após resposta ambígua do provedor, ENTÃO a reserva DEVE permanecer inalterada e a ocorrência DEVE ser observável.
- SE cancelamento ou expiração vencer a corrida, ENTÃO a operação concorrente DEVE afetar zero linhas e não incrementar capacidade novamente.

## Rastreabilidade de requisitos

| ID | História | Fase | Estado |
| --- | --- | --- | --- |
| DEMO-01 | Gerenciar eventos | Design | Em design |
| DEMO-02 | Reservar sem oversell | Design | Em design |
| DEMO-03 | Cancelar e expirar | Design | Em design |
| DEMO-04 | Idempotência e erros | Design | Em design |
| DEMO-05 | Executar e provisionar | Design | Em design |
| DEMO-06 | Notificar a reserva | Design | Em design |
| DEMO-07 | Proteger a API e limitar abuso | Design | Em design |

**Cobertura:** 7 requisitos, 7 mapeados ao design, nenhum sem mapeamento.

## Critérios de sucesso

- [ ] Os cinco endpoints passam nos testes de contrato.
- [ ] Nenhum teste concorrente produz oversell.
- [ ] Cancelamento e expiração devolvem capacidade uma vez.
- [ ] Expiração saudável conclui devolução até expiresAt + 5 segundos, inclusive pelo reconciliador na ausência de mensagem.
- [ ] CANCELLED e EXPIRED preservam código e descrição do motivo de encerramento.
- [ ] Os dois GET usam cache Valkey com TTL máximo de um segundo e invalidação pós-commit.
- [ ] Toda reserva possui cliente ligado por chave estrangeira e envia notificação assíncrona sem prometer compra.
- [ ] Consultas e comandos executam em serviços separados usando a mesma imagem e as mesmas regras de negócio.
- [ ] Requisições anônimas não alcançam os containers e excesso recebe `429` na borda.
- [ ] Docker Compose inicia a solução completa.
- [ ] Terraform representa todos os recursos AWS da demo.
