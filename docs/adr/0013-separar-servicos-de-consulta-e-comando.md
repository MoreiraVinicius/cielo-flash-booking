# Separar serviços de consulta e comando usando o mesmo artefato Java

- Estado: aceita
- Referências: [design da demo](../../.specs/features/flash-booking-demo/design.md), [design de alta carga](../../.specs/features/flash-booking-high-load/design.md), [avaliação do case](../case-requirements-evaluation.md).

## Contexto

Consultas e comandos têm perfis de carga diferentes. Leituras podem crescer perto do evento ou durante divulgação; reservas crescem abruptamente na abertura da venda e pressionam a linha de inventário. Um único serviço ECS escalado por média de CPU mistura sinais, pode criar containers de escrita para atender leitura e aumenta conexões ao banco sem necessidade.

## Decisão

Construir uma única imagem e um único binário Java com três modos de execução selecionados por configuração: `query-api`, `command-api` e `worker`. O modo de consulta expõe somente os dois GETs; o modo de comando expõe somente os POSTs e o DELETE; o worker executa outbox, expiração, reconciliação e notificação.

Os modos compartilham os mesmos módulos de domínio, casos de uso, entidades, migrations e integrações. A configuração apenas decide quais controllers e consumidores são iniciados. Nenhuma regra de negócio pode depender de quantidade de tasks, ambiente, volume ou modo de implantação.

Na demo existe um serviço ECS para cada modo, com uma task e tetos pequenos. Na arquitetura de alta carga os mesmos serviços e a mesma imagem usam mínimos, máximos e políticas de escala independentes: consultas por requisições/latência/cache; comandos por requisições/latência/conexões; workers por backlog por task e idade da mensagem.

## Alternativas consideradas

Um único serviço HTTP é mais curto, mas impede escala independente e mistura permissões. Binários ou repositórios diferentes poderiam otimizar cada caminho, porém criariam divergência de regras, releases e contratos, contrariando a exigência de que apenas a infraestrutura varie. Microserviços de leitura e escrita com modelos próprios seriam excesso de arquitetura para cinco endpoints.

## Consequências

O API Gateway e o ALB precisam rotear por método e caminho para target groups distintos. O pipeline futuro publica uma imagem imutável única; cada task definition altera somente o modo e a configuração. Testes de arquitetura devem impedir dependências de domínio para controllers, AWS ou perfis de runtime.
