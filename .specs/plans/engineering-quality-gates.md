# Qualidade executável e revisão AWS do Flash Booking

> Build this with **tlc-implement**.
> Every criterion below becomes a check with a proof, referenced by its number. Nothing under
> `Unresolved` gets settled while building.

## Intent

Os limites da arquitetura Java existem em documentação, mas ainda não falham o build quando uma dependência os viola. A formatação também depende de revisão humana. A arquitetura AWS da demo e o desenho high-load ainda não passaram por uma revisão Well-Architected rastreável. Quem mantém ou avalia o projeto paga com revisão repetitiva e com risco de aceitar regressões estruturais ou afirmações cloud sem evidência.

O build passa a provar as fronteiras essenciais com ArchUnit e a formatação Java com Spotless. Uma revisão AWS quick registra riscos e recomendações separando o ambiente demo da arquitetura high-load. Reorganizações internas de pacotes são permitidas quando corrigem acoplamento ou tornam as responsabilidades mais claras. O comportamento da aplicação, o schema e os recursos AWS não mudam.

8 critérios em 3 slices · 3 one-way doors · 0 open, 0 blocks

## Criteria

### Arquitetura Java executável

1. Quando `mvnw.cmd --batch-mode clean verify -Pintegration` for executado no código aceito, então o build termina com exit code `0` e executa as regras ArchUnit junto dos testes existentes.
2. Se uma classe do domínio puro depender de Spring, Jakarta, Jackson, AWS SDK, JDBC ou de componentes de aplicação, transporte ou infraestrutura, então a suíte ArchUnit termina com exit code diferente de `0` e identifica a dependência proibida. O escopo inicial é `com.cielo.flashbooking.domain..`; mudanças de localização atualizam a seleção da regra no mesmo commit. A regra não proíbe Spring nos serviços de aplicação.
3. Se dois módulos explicitamente mapeados no teste de arquitetura formarem um ciclo de dependências, então a suíte ArchUnit termina com exit code diferente de `0` e identifica os módulos e as dependências do ciclo. O mapa inclui cada classe de produção em exatamente um módulo, distingue capacidades de negócio de suporte técnico e falha para classes não classificadas. Pacotes de primeiro nível só podem ser usados diretamente quando representarem esse mapa; o nome da pasta, sozinho, não define um módulo. O teste inclui prova negativa com um ciclo entre dois módulos para impedir aprovação por agrupamento de toda a aplicação em um único módulo.
4. Se código de domínio ou aplicação depender de uma implementação concreta de adapter, então a suíte ArchUnit termina com exit code diferente de `0` e identifica a dependência. Wiring de infraestrutura pode referenciar implementações para compor a aplicação; essa permissão fica restrita às classes de configuração enumeradas no mapa, sem liberar um pacote inteiro por conveniência. Controllers e consumidores continuam chamando casos de uso ou portas, não implementações de persistência.

### Formatação Java reproduzível

5. Quando `mvnw.cmd spotless:check` for executado sobre `src/main/java/**/*.java` e `src/test/java/**/*.java` no código aceito, então o comando termina com exit code `0`.
6. Se um arquivo Java dentro do escopo configurado violar o formatter, então `mvnw.cmd verify` termina com exit code diferente de `0` e imprime o caminho do arquivo divergente.
7. Quando `mvnw.cmd spotless:apply` for executado duas vezes sobre a mesma árvore, então a segunda execução não produz alteração de conteúdo e `mvnw.cmd spotless:check` termina com exit code `0`.

### Revisão AWS proporcional

8. Quando a revisão quick `aws-well-architected-review` for concluída, então `.specs/research/aws-well-architected-quick-review.md` registra os seis pilares, distingue demo atual de high-load documentado e classifica cada item como `atendido no escopo analisado`, `risco aceito`, `remediação proposta`, `não aplicável` ou `evidência insuficiente`. Cada conclusão cita arquivo e linha ou fonte operacional verificável; lacunas identificam a evidência ausente e como obtê-la. `Risco aceito` exige referência a uma decisão explícita do responsável, e `não aplicável` exige justificativa. Código Terraform e desenho não comprovam que um recurso esteja ativo nem que alta carga tenha sido validada em execução.

## Out of scope

- Maven Enforcer - excluído pelo responsável neste ciclo.
- TFLint, Trivy e Checkov - excluídos pelo responsável neste ciclo.
- Spring Modulith - ArchUnit prova as fronteiras necessárias sem introduzir outro modelo de módulos.
- Converter o monólito modular em microserviços, criar serviços ou abstrações especulativas - reorganizar pacotes não autoriza mudar a arquitetura de deployment.
- Corrigir automaticamente achados da revisão AWS - a revisão produz evidência e recomendações; cada remediação exige decisão própria.
- Executar `terraform apply`, `terraform destroy`, deploy ou qualquer mutação na conta AWS - revisão e build são locais e read-only.
- Formatar Terraform, Markdown, YAML ou JavaScript com Spotless - este ciclo cobre somente Java de produção e teste.

## Observable

| Surface | Decision | Landing |
| --- | --- | --- |
| comando `mvnw.cmd clean verify -Pintegration` | saída de sucesso e exit code | 1 |
| comando `mvnw.cmd clean verify -Pintegration` | falha arquitetural identifica dependência ou ciclo | 2, 3, 4 |
| comando `mvnw.cmd spotless:check` | escopo e saída de sucesso | 5 |
| comando `mvnw.cmd verify` | falha de formatação identifica o arquivo | 6 |
| comando `mvnw.cmd spotless:apply` | repetição não altera conteúdo | 7 |
| documento `aws-well-architected-quick-review.md` | estrutura: seis pilares, evidência, contexto e classificação | 8 |
| documento `aws-well-architected-quick-review.md` | tom: evidência antes da recomendação; demo não é tratada como high-load | 8 |
| documento `aws-well-architected-quick-review.md` | próximo passo: cada remediação proposta fica explícita e não executada | 8 |
| coleção de regras ArchUnit | agrupamento: domínio puro, módulos explicitamente mapeados, direção para adapters e composição de infraestrutura | 2, 3, 4 |
| coleção de regras ArchUnit | duplicatas: uma regra por invariante arquitetural | 2, 3, 4 |
| coleção de regras ArchUnit | exceções: wiring explicitamente enumerado; sem exclusão ampla, baseline congelada ou regra desabilitada para ocultar falha | 2, 3, 4 |

## Swept

- validation: 1, 2, 3, 4, 5, 6, 7
- failure modes: 2, 3, 4, 6
- idempotency and retry: 7
- authorization: n/a - não há alteração de API, autenticação ou acesso ao runtime
- concurrency and ordering: n/a - os checks analisam bytecode e arquivos sem estado compartilhado de runtime
- data lifecycle: n/a - schema e dados persistidos não mudam
- external-dependency failure: existing - Maven já termina com exit code diferente de `0` quando não resolve uma dependência; a revisão AWS deve registrar fonte inacessível em vez de inventar evidência
- state transitions: n/a - nenhum ciclo de vida do produto muda
- observability: 1, 2, 3, 4, 5, 6, 7, 8

## Impact

| Front | What changes |
|---|---|
| build Java | `pom.xml` passa a executar ArchUnit e Spotless pelo fluxo Maven existente |
| testes | a suíte ganha regras arquiteturais que falham por dependência ou ciclo proibido |
| organização Java | pacotes, imports, testes, configuração por nome de classe e referências nas specs acompanham cada movimentação; contratos HTTP, transações, propriedades e perfis Spring permanecem iguais |
| CI | o job Java existente continua usando `clean verify -Pintegration`; os novos checks entram no mesmo exit code |
| documentação | uma revisão AWS quick passa a registrar evidência e próximos passos em `.specs/research/` |
| domínio | nenhum termo ou comportamento muda |
| stored data | nothing to migrate |

## Decided

| Decision | Shape | Alternative rejected |
|---|---|---|
| Arquitetura Java vira regra executável | dependência de teste `com.tngtech.archunit:archunit-junit5`; invariantes dos critérios 2, 3 e 4 | Spring Modulith adiciona um modelo de módulos maior que a pressão atual |
| Formatação Java vira gate do Maven | plugin `com.diffplug.spotless:spotless-maven-plugin`; `check` participa de `verify`; `apply` permanece comando explícito | formatação somente na IDE não é reproduzível na CI |
| Revisão cloud é proporcional | modo quick da skill `aws-well-architected-review`; relatório separa demo de high-load | revisão completa neste ciclo adiciona custo sem decisão de remediação ou produção high-load |

## Implementation guidance

- Registrar o mapa de responsabilidades antes de escrever as regras. `event`, `inventory`, `reservation` e `notification` são capacidades existentes; domínio puro, idempotência, outbox, transporte e composição são responsabilidades distintas, não capacidades inventadas.
- A inspeção encontrou `application/reconciliation/ExpirationReconciler` consumindo `reservation.application.ReservationReader`, enquanto `reservation/application/GetReservationService` usa `application.error.ResourceNotFoundException`. `application/outbox/OperationalDataCleaner` também coordena notificações. Esses imports provam um ciclo no agrupamento técnico atual, não um ciclo entre beans Spring.
- Preferir movimentações pequenas para colocar reconciliação/expiração junto de reservas e separar a coordenação de limpeza das portas compartilhadas, se isso eliminar a mistura observada. Não mover classes apenas para satisfazer uma expressão de pacote. O destino exato é uma decisão reversível da implementação, acompanhada de diff e testes.
- Não congelar violações existentes nem remover classes do universo analisado para obter build verde. Se uma fronteira estiver mal definida, corrigir o mapa e sua justificativa antes de fixar o checklist de execução.
- Separar a normalização mecânica do formatter da reorganização funcional nos commits. Versões do formatter e dos plugins ficam fixadas no POM; Maven Enforcer não faz parte da solução.
- Após movimentações, executar a suíte existente com `clean verify -Pintegration`, além das provas negativas de arquitetura e formatação em scratch. Sem ambiente de integração disponível, registrar verificação pendente, nunca declarar sucesso integral.

## Sources

- `.specs/research/global-engineering-skills.md` - seleciona ArchUnit, Spotless e revisão AWS; exclui Enforcer e scanners IaC.
- `.specs/STATE.md` - fixa Java 21, Spring Boot 3, monólito modular, ECS antes de EKS e high-load apenas documentado.
- `pom.xml` - estabelece Maven, Java 21, Spring Boot 3.5.0 e o fluxo de testes atual.
- `.github/workflows/ci.yml` - estabelece `clean verify -Pintegration` como gate Java da CI.
- `C:\Users\vinic\java-spring-agent-guidance\RESEARCH.md` - fundamenta ArchUnit e formatação automatizada para a orientação Java/Spring existente.
- conversa de 2026-09-27 - exclui TFLint, Trivy e Maven Enforcer; mantém o restante da recomendação.
- conversa de 2026-09-27, revisão autorizada: "aplique as suas orientações para não serem tão restristivas. Se precisar reeogonizar pacotes faça". User delegated: critérios proporcionais e reorganização interna necessária, preservando contratos.

Este plano registra a decisão dentro de `.specs/`, conforme AD-022. Uma divergência factual deve ser corrigida com evidência; nova capacidade, mudança de contrato ou conflito com uma decisão ativa exige decisão explícita antes de construir.

## Unresolved

| # | Kind | Question | Until answered |
|---|---|---|---|
| - |  | None |  |
