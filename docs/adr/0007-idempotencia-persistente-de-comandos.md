# Persistir idempotência de comandos por 24 horas

- Estado: aceita
- Referências: [especificação da demo](../../.specs/features/flash-booking-demo/spec.md), [design da demo](../../.specs/features/flash-booking-demo/design.md), [ADR de PostgreSQL](0004-postgresql-como-fonte-autoritativa.md).

## Contexto

Repetições de comandos podem ocorrer por timeout, perda da resposta após commit ou retry do cliente. A reserva deve produzir um único efeito mesmo com múltiplas instâncias. Um cache não é adequado como registro de idempotência: pode expirar, ser perdido e não participa da transação que atualiza estoque.

## Decisão

Exigir `Idempotency-Key` em cada endpoint mutável: `POST /events`, `POST /events/{id}/reservations` e `DELETE /reservations/{id}`. Persistir no PostgreSQL, por uma janela de 24 horas, a chave, operação HTTP, alvo normalizado, hash do payload, status e corpo da resposta final. A duração é o contrato operacional inicial para cobrir retries tardios sem reter indefinidamente respostas e dados de clientes; sua calibração futura depende de métricas de retries reais.

Enquanto `expires_at` estiver no futuro, chave repetida com a mesma operação, alvo e hash retorna exatamente a resposta persistida; reutilização com operação, alvo ou hash diferente retorna `409`. No vencimento decidido por `clock_timestamp()` do PostgreSQL, a próxima tentativa insere logicamente um novo comando ao substituir atomicamente o registro anterior; concorrentes continuam serializados pela unicidade e somente um executa o novo efeito. Respostas de domínio finais, inclusive `409` por falta de capacidade, são persistidas para preservar o resultado original; falhas transitórias `5xx` não são persistidas e podem ser repetidas. A gravação da resposta e a mutação de domínio ocorrem na mesma transação quando houver mudança de estado; uma rejeição sem mutação registra sua resposta em transação própria após adquirir a chave.

O worker remove registros vencidos em lotes limitados, ordenados pelo vencimento e conquistados com `FOR UPDATE SKIP LOCKED`. O padrão da v1 é até 500 linhas a cada cinco segundos; tamanho, intervalo e atraso inicial pertencem à configuração tipada `idempotency.cleanup`. Essa limpeza controla armazenamento e retenção, mas não participa da validade: uma chave vencida pode ser reivindicada mesmo que a linha antiga ainda exista, e a limpeza revalida o vencimento antes de excluir para não apagar uma aquisição concorrente.

## Alternativas consideradas

Idempotência em memória não funciona com múltiplas tasks. Valkey reduziria latência, mas a perda de cache reabriria a janela de duplicidade e exigiria uma fonte durável de qualquer forma. Não exigir chave deixa o resultado de retries dependente de timing e não permite provar o contrato.

## Consequências

O cliente deve preservar a chave durante toda a tentativa e não deve esperar replay após 24 horas. A remoção física pode atrasar em relação ao vencimento sem alterar o contrato, e uma indisponibilidade prolongada do worker pode aumentar temporariamente a tabela. A unicidade, o relógio e o hash pertencem ao banco autoritativo; portanto, a troca de RDS PostgreSQL por Aurora PostgreSQL não muda código, schema ou semântica.
