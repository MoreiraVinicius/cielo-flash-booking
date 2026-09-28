# Desenho dos gates

## Mapa de módulos

| Módulo | Responsabilidade | Dependências permitidas no desenho atual |
| --- | --- | --- |
| `domain` | Entidades e invariantes puras | Java e próprio domínio |
| `application` | Erros compartilhados, idempotência e portas de outbox | `domain` quando necessário |
| `inventory` | Porta e aplicação de inventário | `domain`, `application` quando necessário |
| `event` | Caso de uso e entrada HTTP de evento | `domain`, `application`, `controller` |
| `reservation` | Casos de uso, expiração, reconciliação e entrada HTTP de reserva | `domain`, `application`, `event`, `inventory`, `controller` |
| `notification` | Entrega assíncrona de e-mail | `reservation`, `application` |
| `operations` | Limpeza operacional coordenada | `application`, `notification` |
| `controller` | Erro HTTP e correlação compartilhados | `application` |
| `config` | Composição e filtros de infraestrutura | módulos chamados pela configuração |
| `adapter` | Persistência, cache e publisher concretos | portas dos módulos de aplicação |

`FlashBookingApplication` é o bootstrap, não um módulo de negócio. Toda outra classe de produção pertence a um único primeiro pacote enumerado. ArchUnit detecta ciclos reais entre esses módulos; testes não excluem dependências existentes nem congelam violações. A regra separada de domínio puro impede que o agrupamento `application` dilua a proteção do núcleo.

Esta escolha está registrada em AD-029. As fixtures de teste com dependências deliberadamente incorretas ficam fora da análise de classes de produção e provam que as regras rejeitam domínio impuro, ciclo e acesso direto a adapter.

## Movimentações de coesão

- `application.reconciliation` passa a `reservation.reconciliation`.
- `feature.reservation.expire` passa a `reservation.expire`.
- `OperationalDataCleaner` e sua configuração passam de `application.outbox` para `operations`; a porta de outbox permanece em `application.outbox`.

Essas movimentações mudam nomes internos de classes, imports e localização de testes. Não mudam valores das propriedades, perfis Spring, scheduler, contratos HTTP nem deploy. Esta alternativa é menor do que criar camadas adicionais ou ignorar ciclos por baseline.

## Formatter e revisão AWS

- Spotless Maven `3.10.3` é ligado ao lifecycle `validate` com `check`; `apply` é comando explícito. Palantir Java Format `2.98.0` formata apenas `src/main/java` e `src/test/java`. Ambos têm versões fixas no POM. A escolha mantém indentação de quatro espaços e linhas mais largas que Google Java Format padrão; o custo é um commit mecânico que normaliza a árvore Java existente.
- A revisão AWS examina código, IaC e specs, sem consultar segredos nem afirmar operação high-load. `Evidência insuficiente` preserva lacunas verificáveis.

## Technical Decisions

| Decisão | Alternativa descartada | Motivo |
| --- | --- | --- |
| Pacotes representam módulos de primeira ordem após as movimentações de coesão | Congelar ciclos existentes ou mapear todos os pacotes como um único módulo | Ocultaria regressões futuras. |
| Manter `application` como suporte compartilhado | Criar novos módulos/ports para cada exceção | Não há pressão concreta para novas abstrações. |
