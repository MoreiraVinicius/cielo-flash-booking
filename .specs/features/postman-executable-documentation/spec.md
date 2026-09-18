# Postman Executable Documentation Specification

## Problem Statement

A coleção Postman atual valida apenas uma demo AWS específica. Ela contém valores pessoais e um endpoint temporário, não possui ambientes reutilizáveis e não ensina alguém novo a exercitar o sistema localmente. O Postman precisa se tornar uma documentação executável para o fluxo do case.

## Goals

- [ ] Oferecer uma única coleção importável com fluxos independentes para Local e AWS.
- [ ] Cobrir os cinco endpoints do case e os cenários críticos de contrato.
- [ ] Versionar ambientes seguros, sem endpoint ativo, credencial ou e-mail pessoal.
- [ ] Explicar como executar, interpretar e estender a coleção.
- [ ] Exibir massas de criação seguras e ensinar o encadeamento de respostas entre requests.
- [ ] Oferecer baterias selecionáveis no Collection Runner para os erros de idempotência Local e AWS.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Provisionar ou reativar a demo AWS | Requer credenciais, custo e autorização externos. |
| Versionar credenciais, tokens ou e-mails verificados | Segredos e dados pessoais não pertencem ao repositório. |
| Automatizar espera de dez minutos para testar o fim de uma venda | A coleção documentará a verificação manual sem introduzir espera longa em um runner. |
| Substituir os testes Java/Testcontainers | Postman prova o contrato HTTP; as garantias de persistência continuam nos testes do projeto. |

---

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Formato da coleção | Postman Collection v2.1 e ambientes v2.1 | É o formato já usado no repositório e pode ser importado pelo Postman/Newman. | yes |
| Runtimes | Uma coleção contém as pastas `Local` e `AWS` | Os fluxos são semelhantes, mas a autenticação é diferente e precisa ser explícita. | yes |
| Autenticação local | `noauth` somente na pasta Local | O Compose não expõe IAM/SigV4. | yes |
| Autenticação AWS | AWS Signature v4 herdada da coleção | A borda AWS exige IAM/SigV4 para cada endpoint. | yes |
| Dados de ambiente | Valores secretos e endereço AWS permanecem vazios | Evita segredos, PII e endpoint temporário versionados. | yes |
| Identidade de cliente padrão | `postman+flash-booking@example.test` | Endereço sintaticamente válido e não pessoal; em AWS deve ser substituído por destinatário SES verificado. | yes |
| Encadeamento da apresentação | IDs são guardados em variáveis da coleção automaticamente | Evita copiar valores manualmente e deixa o fluxo visível no Postman. | yes |
| Bateria de idempotência | Os folders Local e AWS continuam autocontidos e podem ser selecionados no Collection Runner | O histórico de Runs não é versionado no JSON; a sequência versionada e os assertions são. | yes |

**Open questions:** none - all resolved or logged above.

---

## User Stories

### P1: Executar o fluxo local ⭐ MVP

**User Story**: Como pessoa que acabou de clonar o projeto, quero importar um ambiente Local e executar o ciclo de evento e reserva para aprender o contrato sem precisar de AWS.

**Why P1**: O ambiente local deve ser a primeira documentação executável do sistema.

**Acceptance Criteria**:

1. WHEN a user selects the Local environment THEN the collection SHALL send commands to `http://localhost:8082` and queries to `http://localhost:8081` without authentication. <!-- event-driven -->
2. WHEN a user runs the Local case flow in order THEN the collection SHALL assert `201` for event and reservation creation, `200` for both reads and cancellation, and persist the generated identifiers for later requests. <!-- event-driven -->
3. WHEN a user replays a reservation with the same idempotency key THEN the collection SHALL assert the original reservation identifier and `201`. <!-- event-driven -->
4. WHEN a user requests capacity greater than the remaining inventory or omits `Idempotency-Key` THEN the collection SHALL assert `409` or `400` respectively and `application/problem+json`. <!-- unwanted-behavior -->

**Independent Test**: Iniciar Compose, selecionar Local, executar a pasta Local e observar todos os assertions verdes.

---

### P1: Executar a demo AWS com segurança

**User Story**: Como operador autorizado, quero selecionar um ambiente AWS vazio e assinar os requests para validar a demo sem reutilizar dados temporários de outra pessoa.

**Why P1**: A infraestrutura expõe apenas uma entrada IAM/SigV4 e a coleção deve ensinar essa fronteira.

**Acceptance Criteria**:

1. WHEN a user selects the AWS environment and supplies credenciais temporárias, URL e e-mail SES válido THEN the collection SHALL sign authenticated requests with AWS Signature Version 4 for `execute-api`. <!-- event-driven -->
2. WHEN a user sends the AWS unsigned-boundary request THEN the collection SHALL assert `403` before continuing with signed requests. <!-- event-driven -->
3. IF the committed AWS environment is imported without local edits THEN the environment SHALL contain no active API URL, access key, secret key, session token or personal e-mail. <!-- unwanted-behavior -->

**Independent Test**: Importar AWS, preencher valores temporários, executar a pasta AWS contra uma demo autorizada e observar primeiro `403` sem assinatura e depois o fluxo assinado.

---

### P2: Usar o Postman como referência de contrato

**User Story**: Como revisor do case, quero descrições e testes legíveis em cada request para entender regras de negócio sem abrir imediatamente o código Java.

**Why P2**: A coleção deve complementar README e testes de integração, não ser apenas um conjunto de URLs.

**Acceptance Criteria**:

1. WHEN a reviewer opens the collection THEN it SHALL document a preparação, a separação entre Command e Query APIs, idempotência, capacidade, cancelamento e janela de venda em nomes ou descrições de pastas e requests. <!-- event-driven -->
2. WHEN the sale-window scenario creates an event starting no futuro THEN the collection SHALL assert `409 application/problem+json` for a reservation before `startsAt`. <!-- event-driven -->
3. WHEN a contributor runs the repository Postman validator THEN it SHALL reject malformed JSON, missing Local/AWS environment variables, missing key assertions, committed active AWS URLs, non-empty AWS secrets or personal e-mail values. <!-- event-driven -->

**Independent Test**: Rodar `powershell -File scripts/validate-postman.ps1` e inspecionar os requests documentados no Postman.

---

### P2: Demonstrar criação de massa e encadeamento

**User Story**: Como apresentador do case, quero mostrar corpos de criação preenchidos e como uma resposta alimenta o próximo request para explicar a demo sem copiar IDs manualmente.

**Why P2**: A coleção precisa ensinar o fluxo enquanto é apresentada, não apenas executá-lo.

**Acceptance Criteria**:

1. WHEN a presenter opens each event or reservation creation request THEN the collection SHALL show an editable JSON body with the required fields and only safe fixture values or declared variables. <!-- event-driven -->
2. WHEN a create response returns an identifier THEN the collection SHALL save `event.id`, `reservation.id` or future `event.id` in a named collection variable and document the exact variable reference consumed by the next request. <!-- event-driven -->

**Independent Test**: Abrir os requests 01, 03 e 10 da pasta Local, enviar 01 e 03, e conferir em Scripts/Tests e Collection variables que os IDs retornados foram armazenados para `{{eventId}}` e `{{reservationId}}`.

---

### P1: Executar a bateria de idempotência ⭐ MVP

**User Story**: Como apresentador, quero executar uma pasta Local ou AWS no Collection Runner e demonstrar tanto o replay seguro quanto os erros de idempotência do contrato.

**Why P1**: A regra é central para o case e precisa ser observável sem abrir os testes Java.

**Acceptance Criteria**:

1. WHEN a presenter runs either the Local or AWS folder in the Collection Runner THEN the collection SHALL include named requests for a replay with the same key and payload, a changed payload with the same key, and the same key used on another endpoint. <!-- event-driven -->
2. WHEN an active `Idempotency-Key` is reused with a changed payload or normalized target THEN the collection SHALL assert `409`, `resource-conflict` and `application/problem+json` in both Local and signed AWS flows. <!-- event-driven -->
3. IF a mutable request omits `Idempotency-Key` or sends a key longer than 128 characters THEN the collection SHALL assert `400`, `invalid-request` and `application/problem+json` in both Local and signed AWS flows. <!-- unwanted-behavior -->
4. WHEN a capacity rejection is replayed with its original key and payload THEN the collection SHALL assert the persisted `409 resource-conflict` response in both Local and signed AWS flows. <!-- event-driven -->

**Independent Test**: No Postman, iniciar Compose, selecionar **Flash Booking Local**, clicar em Run no folder Local e observar as requests de idempotência verdes; em uma demo AWS autorizada, preencher o ambiente e executar o folder AWS para observar os mesmos assertions assinados.

## Edge Cases

- IF a user runs AWS requests with blank environment variables THEN Postman SHALL not contain fallback credentials or a default active endpoint.
- WHEN the sale-window request chooses a start instant in the future THEN the collection SHALL create an event whose `startsAt` is asserted before testing the `409` reservation.
- IF an API returns a problem response THEN collection tests SHALL assert the `application/problem+json` content type in addition to its status.
- WHEN a presenter resends the first creation request THEN the collection SHALL reset the generated identifiers before it stores the identifiers from the new run.
- IF a user runs a folder without preencher o ambiente AWS THEN the signed idempotency battery SHALL not fabricate credentials or a fallback URL.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| POSTMAN-01 | P1: Executar o fluxo local | Execute | Verified |
| POSTMAN-02 | P1: Executar o fluxo local | Execute | Verified |
| POSTMAN-03 | P1: Executar o fluxo local | Execute | Verified |
| POSTMAN-04 | P1: Executar a demo AWS com segurança | Execute | Verified |
| POSTMAN-05 | P1: Executar a demo AWS com segurança | Execute | Verified |
| POSTMAN-06 | P2: Usar o Postman como referência de contrato | Execute | Verified |
| POSTMAN-07 | P2: Usar o Postman como referência de contrato | Execute | Verified |
| POSTMAN-08 | P2: Demonstrar criação de massa e encadeamento | Execute | Verified |
| POSTMAN-09 | P2: Demonstrar criação de massa e encadeamento | Execute | Verified |
| POSTMAN-10 | P1: Executar a bateria de idempotência | Execute | Verified |
| POSTMAN-11 | P1: Executar a bateria de idempotência | Execute | Verified |
| POSTMAN-12 | P1: Executar a bateria de idempotência | Execute | Verified |
| POSTMAN-13 | P1: Executar a bateria de idempotência | Execute | Verified |

**Coverage:** 13 total, 0 mapped to tasks, 13 unmapped.

## Success Criteria

- [ ] Um usuário local executa os cinco endpoints e os cenários de erro principais pelo Postman.
- [ ] Um operador AWS tem instruções e ambiente seguro para SigV4 sem valores temporários no Git.
- [ ] O repositório rejeita estruturalmente uma coleção ou ambiente incompletos.
- [ ] Um apresentador encontra os Bodies de criação e explica o caminho `response.id` → variável → próximo request sem edição manual.
- [ ] Um apresentador executa Local ou AWS no Collection Runner e mostra os quatro resultados de idempotência esperados.
