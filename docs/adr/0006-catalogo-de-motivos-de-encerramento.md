# Definir catálogo de motivos de encerramento de reserva

- Estado: aceita
- Referências: [modelo de reserva](0002-reserva-temporaria-com-motivo-de-encerramento.md), [especificação da demo](../../.specs/features/flash-booking-demo/spec.md), [glossário](../../CONTEXT.md).

## Contexto

Uma reserva não representa compra confirmada. Quando ela deixa `PENDING`, o caso exige que código e descrição do motivo fiquem preservados. Sem um catálogo estável, consumidores não conseguem distinguir cancelamento solicitado de expiração e o campo de descrição pode virar texto inconsistente.

## Decisão

O servidor atribui os motivos; `DELETE /reservations/{id}` não recebe motivo informado pelo cliente.

| Estado terminal | `closureReason.code` | `closureReason.description` |
| --- | --- | --- |
| `CANCELLED` | `CANCELLED_BY_REQUEST` | `Reserva cancelada por solicitação` |
| `EXPIRED` | `RESERVATION_DEADLINE_REACHED` | `Prazo da reserva encerrado` |

O motivo é gravado na mesma transação que a mudança de estado e a devolução de estoque. `GET /reservations/{id}` retorna `closureReason` como objeto com `code` e `description` para estados terminais, e `null` para `PENDING`. A descrição é controlada pelo servidor e não pode ser atualizada após o encerramento.

## Alternativas consideradas

Aceitar texto livre no cancelamento flexibilizaria a API, mas não acrescenta requisito de produto e reduziria a comparabilidade operacional. Guardar apenas o estado não atende à necessidade de auditoria. Usar apenas código sem descrição transfere a responsabilidade de apresentação para todo consumidor, sem benefício para o case.

## Consequências

O schema requer campos imutáveis de motivo para estados terminais e uma constraint que impeça motivo ausente ou alteração após o fechamento. Novos motivos exigem nova decisão de contrato e migração compatível. Não há mudança de infraestrutura AWS.
