# Publicar a demo e entregar alta carga sem provisionamento remoto

- Estado: aceita
- Referências: [enunciado](../../Case%20BackEnd%201.md), [estado do projeto](../../.specs/STATE.md).
- Decisão relacionada: AD-006 em `.specs/STATE.md`, substitui AD-003 quanto à entrega e validação remota.

## Contexto e restrições

A entrega ocorre hoje, com poucas horas disponíveis. O enunciado de referência é `Case BackEnd 1.md`. O usuário dispõe de US$100 em créditos AWS e será o único usuário do ambiente publicado. A demo ficará ligada por no máximo 1h30. AWS e Terraform são escolhas explícitas do usuário; não devem ser atribuídos ao enunciado como exigência textual.

## Alternativas consideradas

1. Executar e demonstrar ambas as arquiteturas na AWS. Permite verificar a infraestrutura de alta carga, mas adiciona gasto e tempo de operação.
2. Entregar somente a demo e documentar alta carga. Reduz trabalho, mas não atende ao desejo de entregar também o código da segunda arquitetura.
3. Publicar a demo e entregar o código da alta carga sem provisioná-la. Escolhida pelo usuário para limitar gasto, preservando o escopo de código desejado.

## Decisão

Entregar e demonstrar a demo na AWS. Entregar o código de aplicação e infraestrutura da alta carga, sem aplicar seu Terraform nem executar testes remotos dessa arquitetura. A demo é a única arquitetura autorizada no escopo de publicação, mediante o fluxo de autorização de operações externas.

## Consequências e limites de evidência

A alta carga não terá comprovação de implantação, failover, desempenho, migração ou recuperação na AWS. Nenhum relatório deverá apresentar essas propriedades como verificadas. Testes locais e validação estática, quando executados, deverão ser identificados separadamente dos testes remotos não realizados.

O prazo é um risco para a entrega de todo o código pretendido. Não há redução automática desse escopo: qualquer corte necessário deverá ser explicitado ao usuário, preservando a prioridade da demo funcional.

A janela de operação foi limitada pelo usuário a 1h30. A estimativa e o procedimento de encerramento estão em [docs/cost-estimate.md](../cost-estimate.md); antes de qualquer provisionamento ainda é obrigatório recalcular os valores para a conta e confirmar quais serviços são cobertos pelos créditos.

## Revisão

Revisitar esta decisão se houver autorização para testar alta carga na AWS, mudança de prazo ou alteração de orçamento. A revisão dos detalhes técnicos permanece aberta em [REVIEW.md](../../.specs/REVIEW.md); este ADR não significa aprovação desses detalhes.
