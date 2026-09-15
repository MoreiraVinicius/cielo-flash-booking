# Modelar reserva temporária com motivo de encerramento

- Estado: aceita
- Referências: [enunciado](../../Case%20BackEnd%201.md), [glossário](../../CONTEXT.md), [especificação demo](../../.specs/features/flash-booking-demo/spec.md).

## Contexto

O [enunciado](../../Case%20BackEnd%201.md) fornece cinco endpoints para criar eventos e gerenciar reservas. O escopo de domínio contempla PENDING, CANCELLED e EXPIRED. Os dois estados terminais precisam preservar código e descrição do motivo para explicar o encerramento da reserva.

## Decisão e justificativa

A reserva representa um bloqueio temporário de ingressos. Aceitar a reserva não confirma uma compra. O modelo permanece com PENDING, CANCELLED e EXPIRED, sem adicionar pagamento, confirmação definitiva ou novo endpoint para compra.

Persistir código e descrição do motivo ao encerrar a reserva em CANCELLED ou EXPIRED. O nome de domínio é motivo de encerramento: não registrar falha de pagamento quando não existe processamento de pagamento neste escopo.

O prazo define a validade, não a fila que primeiro tenta encerrar a reserva. Uma solicitação de cancelamento decidida antes de `expiresAt` produz CANCELLED. Em `expiresAt` ou depois, o PostgreSQL materializa EXPIRED, inclusive se o gatilho for `DELETE /reservations/{id}`.

## Alternativas e consequências

Adicionar confirmação definitiva exigiria ampliar estados e contrato sem atender a um requisito do escopo atual. Guardar apenas o estado não permite registrar a causa de encerramento com código e descrição.

O armazenamento e o contrato de consulta contemplam os motivos. O catálogo de códigos, a origem da descrição e a apresentação no GET estão definidos no [ADR 0006](0006-catalogo-de-motivos-de-encerramento.md). O prazo de devolução de capacidade está no [ADR 0003](0003-prazo-de-liberacao-de-reservas-expiradas.md).
