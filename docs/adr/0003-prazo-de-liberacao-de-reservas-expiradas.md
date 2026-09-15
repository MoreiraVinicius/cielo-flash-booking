# Limitar a cinco segundos a liberação de reservas expiradas

- Estado: aceita
- Referências: [semântica de reserva](0002-reserva-temporaria-com-motivo-de-encerramento.md), [spec demo](../../.specs/features/flash-booking-demo/spec.md), [design demo](../../.specs/features/flash-booking-demo/design.md).

## Contexto

A reserva bloqueia ingressos até expiresAt. A expiração é processada por worker, com mensagens e reconciliação de reservas vencidas. O vencimento lógico e o commit que devolve a capacidade não ocorrem necessariamente no mesmo instante.

Enquanto esse commit não termina, os ingressos permanecem indisponíveis para uma nova reserva. Esse atraso reduz disponibilidade temporariamente, mas não deve permitir oversell nem estender a validade da reserva original.

## Decisão

Com banco e processamento de expiração saudáveis, concluir a transição para EXPIRED e a devolução da capacidade até expiresAt + 5 segundos. A reserva deixa de ser válida em expiresAt; os cinco segundos não são uma extensão de validade.

Persistir estado e motivo de encerramento e devolver capacidade na mesma transação. Duplicidades e concorrência com cancelamento não podem devolver capacidade mais de uma vez nem sobrescrever um encerramento já efetivado.

O PostgreSQL conquista o lock da reserva antes de observar o instante que decide o motivo. `DELETE /reservations/{id}` retorna CANCELLED somente quando esse instante é anterior a `expiresAt`; em `expiresAt` ou depois, a chamada retorna e persiste EXPIRED. Uma requisição iniciada antes do prazo não adquire direito a cancelar se só conquistar o lock depois do vencimento.

O limite se refere à disponibilidade autoritativa no banco, não à atualização de caches de leitura. A defasagem de cache é tratada separadamente no [ADR 0005](0005-cache-valkey-compartilhado-e-binario-unico.md).

## Alternativas e justificativa

- Liberação exatamente em expiresAt: não permite a latência de entrega, processamento e commit do fluxo assíncrono adotado.
- Expiração sem limite de atraso: permite estoque bloqueado indefinidamente sem violação mensurável do contrato.
- Até cinco segundos: limita a indisponibilidade temporária e estabelece um critério observável para o fluxo normal.

## Verificação e limites

Testar ausência de liberação antecipada, `DELETE` dos dois lados de `expiresAt`, espera por lock atravessando o prazo, commit até o limite, motivo persistido e devolução única sob duplicidade e disputa com cancelamento. Repetir o teste de prazo com ausência da mensagem de expiração e reconciliador saudável. Medir do expiresAt persistido até a devolução confirmada no banco.

O intervalo de varredura, tempo de espera e processamento devem caber juntos no limite. A configuração exata será definida no design e comprovada pelos testes; este ADR não declara desempenho já medido.

Após indisponibilidade de banco ou do processamento de expiração, o reconciliador processa reservas atrasadas de forma idempotente; a duração real da recuperação permanece evidência operacional pendente em [REVIEW.md](../../.specs/REVIEW.md). O envelope de carga no qual comprovar a meta também permanece pendente. Na alta carga, este contrato é herdado pelo código, sem alegação de validação remota, conforme [ADR 0001](0001-demo-publicada-alta-carga-sem-provisionamento.md).
