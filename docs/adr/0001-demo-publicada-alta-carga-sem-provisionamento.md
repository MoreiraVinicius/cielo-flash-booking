# Publicar a demo e documentar a evolução de alta carga

- Estado: aceita
- Referências: [enunciado](../../Case%20BackEnd%201.md), [estado do projeto](../../.specs/STATE.md).
- Decisão relacionada: AD-006 em `.specs/STATE.md`, substitui AD-003 quanto à entrega e validação remota.

## Contexto e restrições

A entrega ocorre hoje, com poucas horas disponíveis. O enunciado de referência é `Case BackEnd 1.md`. O usuário dispõe de US$100 em créditos AWS e será o único usuário do ambiente publicado. A demo ficará ligada por no máximo 1h30. AWS e Terraform são escolhas explícitas do usuário; não devem ser atribuídos ao enunciado como exigência textual.

## Alternativas consideradas

1. Executar e demonstrar ambas as arquiteturas na AWS. Permite verificar a infraestrutura de alta carga, mas adiciona gasto e tempo de operação.
2. Entregar a demo e documentar a alta carga como evolução orientada por evidência. Escolhida para manter o desenho revisável sem antecipar uma segunda implementação.
3. Publicar a demo e implementar também a infraestrutura de alta carga sem provisioná-la. Rejeitada porque código não executado criaria uma falsa sensação de entrega e aumentaria a superfície de manutenção.

## Decisão

Entregar e demonstrar a demo na AWS. Manter a alta carga como arquitetura-alvo, com requisitos, decisões, riscos e tarefas futuras, sem código ou infraestrutura próprios nesta versão. A demo é a única arquitetura implementada e autorizada para publicação.

## Consequências e limites de evidência

A alta carga não terá comprovação de implantação, failover, desempenho, migração ou recuperação na AWS. Nenhum relatório deverá apresentar essas propriedades como verificadas. Testes locais e validação estática, quando executados, deverão ser identificados separadamente dos testes remotos não realizados.

Uma futura implementação da alta carga deve começar pelos gargalos medidos e atualizar as especificações antes de adicionar topologia ou código.

A janela de operação foi limitada pelo usuário a 1h30. A estimativa e o procedimento de encerramento estão em [docs/cost-estimate.md](../cost-estimate.md); antes de qualquer provisionamento ainda é obrigatório recalcular os valores para a conta e confirmar quais serviços são cobertos pelos créditos.

## Revisão

Revisitar esta decisão se houver autorização para testar alta carga na AWS, mudança de prazo ou alteração de orçamento. A revisão dos detalhes técnicos permanece aberta em [REVIEW.md](../../.specs/REVIEW.md); este ADR não significa aprovação desses detalhes.
