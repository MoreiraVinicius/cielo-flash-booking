# Gates de engenharia e revisão AWS

Sources:

- `.specs/plans/engineering-quality-gates.md` - critérios e exclusões revisados pelo usuário.
- `.specs/features/engineering-quality-gates/spec.md` - resultados observáveis.
- `.specs/features/engineering-quality-gates/design.md` - módulos e movimentações internas.
- `.specs/STATE.md` - AD-002, AD-006, AD-022 e demais decisões ativas.

## Out of scope

- Maven Enforcer, TFLint, Trivy e Checkov.
- Microserviços, mudança de schema, contratos HTTP, deployment AWS e correção automática dos achados cloud.

## Landing

Os testes ArchUnit entram no build Maven existente. O formatter cobre somente Java; o relatório AWS distingue implementação local, recursos da demo e arquitetura futura.

| One-way door | Literal shape | Alternative rejected |
| --- | --- | --- |
| ArchUnit na suíte de testes | `archunit-junit5` versionado como dependência de teste | Regra apenas documental não falha na CI. |
| Formatter no lifecycle Maven | `spotless-maven-plugin` versionado, `check` no `validate`, `apply` manual | Configuração de IDE não é verificável no build. |
| Pacotes como módulos | Raízes de pacote enumeradas no desenho, nenhuma classe de produção fora delas, bootstrap explícito | Baseline de ciclos esconderia regressões. |

## Checks

### S1 - Arquitetura Java · POM, wrapper, 13 classes e testes · ~16k

**C1** - `mvnw.cmd --batch-mode clean verify -Pintegration` termina com código `0` e executa `ArchitectureTest` junto dos testes existentes em ambiente com dependências e Docker disponíveis.
Proof: `mvnw.cmd --batch-mode clean verify -Pintegration` e relatório `target/surefire-reports/com.cielo.flashbooking.architecture.ArchitectureTest.txt`.

**C2** - Uma classe de `domain..` que depende de qualquer pacote externo listado no critério 2 é recusada com origem e destino identificáveis.
Proof: `mvnw.cmd --batch-mode -Dtest=ArchitectureTest#domainRemainsPure+domainViolationFixtureFails test`.

**C3** - Cada classe de produção pertence a uma raiz de módulo enumerada ou é `FlashBookingApplication`; duas raízes com ciclo de dependências falham com o ciclo identificado.
Proof: `mvnw.cmd --batch-mode -Dtest=ArchitectureTest#moduleMapCoversProductionClasses+modulesRemainAcyclic test` e fixture de ciclo.

**C4** - Domínio, aplicação, controllers e consumidores não dependem de implementações em `adapter..`; composição explícita permanece possível.
Proof: `mvnw.cmd --batch-mode -Dtest=ArchitectureTest#innerAndDeliveryCodeAvoidOutboundAdapters+adapterViolationFixtureFails test`.

### S2 - Formatação Java · POM e árvore Java · ~40k

**C5** - `spotless:check` passa em todo Java de produção e teste.
Proof: `mvnw.cmd --batch-mode spotless:check`.

**C6** - Um arquivo Java em escopo deliberadamente desformatado faz `verify` falhar e imprime seu caminho.
Proof: `mvnw.cmd --batch-mode verify` em cópia isolada contendo um único arquivo desformatado.

**C7** - Duas execuções consecutivas de `spotless:apply` são idempotentes, e `spotless:check` passa depois delas.
Proof: `mvnw.cmd --batch-mode spotless:apply` duas vezes, hashes da árvore Java antes/depois da segunda execução e `mvnw.cmd --batch-mode spotless:check`.

### S3 - Revisão AWS · IaC e specs · ~70k

**C8** - Relatório quick cobre seis pilares e classifica separadamente demo e high-load com evidência ou lacuna explícita; nenhum risco é aceito sem decisão.
Proof: `.specs/research/aws-well-architected-quick-review.md` revisado contra os arquivos/linhas citados e o corpus AWS vivo validado.

## Swept

- validation: C1–C7.
- failure modes: C2–C4, C6.
- idempotency and retry: C7.
- authorization: não há mudança em IAM nem API; alteração remota fora de escopo.
- concurrency and ordering: testes de bytecode e formatter não mudam o runtime concorrente.
- data lifecycle: schema e dados não mudam.
- dependency failure: indisponibilidade de Docker, Maven ou documentação AWS fica inconclusiva; não conta como passe.
- state transitions: nenhum estado de reserva muda.
- observability: C2–C4 e C6 identificam origem; C8 documenta evidência e lacunas.

## Handoff

S1–S3 somam ~126k tokens de leitura estimada e cabem em um lote de 150k; a revisão AWS começa após os gates Java e usa superfícies distintas. Um único agente constrói; a verificação final é independente.
