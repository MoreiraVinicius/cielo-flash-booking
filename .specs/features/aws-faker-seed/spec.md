# Semeadura AWS com Faker.js

## Problem Statement

A demo AWS precisa de uma massa persistida de mil clientes sintéticos para apresentação. O harness de carga existente é intencionalmente local e não assina chamadas IAM/SigV4; usá-lo contra a AWS violaria seus guardrails.

## Goals

- [ ] Criar um seeder Node.js separado que use Faker.js para dados variados e e-mails não entregáveis `@example.test`.
- [ ] Executar uma prova bem-sucedida dos cinco endpoints públicos antes da massa.
- [ ] Persistir exatamente 1.000 clientes únicos e suas reservas aceitas em um evento dedicado, sem exceder quatro comandos por segundo.
- [ ] Autenticar com um perfil AWS local e credenciais temporárias da `ApiInvokerRole`, sem registrar segredos ou e-mails no relatório.

## Out of Scope

| Item | Motivo |
| --- | --- |
| Medir capacidade ou afirmar desempenho produtivo | Esta é uma semeadura de dados, não um teste de carga. |
| Reativar notificações | O consumidor permanece pausado para evitar envio via SES. |
| Alterar dados existentes ou excluir a massa criada | A massa deve permanecer no PostgreSQL. |

## Assumptions & Open Questions

| Assumption / decision | Chosen default | Rationale | Confirmed? |
| --- | --- | --- | --- |
| Volume | exatamente 1.000 reservas aceitas, cada uma com e-mail único | Garante ao menos 1.000 clientes persistidos sem criar volume extra. | yes, usuário |
| Ritmo | no máximo 4 comandos por segundo | Atinge o volume em cerca de cinco minutos com margem para a prova dos endpoints. | yes, decisão anterior |
| E-mails | `@example.test` | São sintéticos e não roteáveis; o consumidor SES está pausado. | yes, usuário |
| Escopo de endpoints | cinco endpoints do case, ao menos uma resposta de sucesso cada | Requisito explícito do usuário. | yes, usuário |

**Open questions:** none.

## User Stories

### P1: Semear clientes persistidos de forma segura

**User Story**: Como operador da demo AWS, quero preencher mil clientes variados com Faker.js sem disparar e-mails reais e sem alterar os dados já existentes.

**Acceptance Criteria**:

1. WHEN o seeder recebe uma URL HTTPS da API, região, perfil AWS e ARN da `ApiInvokerRole` THEN ele SHALL obter credenciais temporárias e assinar cada chamada com SigV4 para `execute-api`, sem gravar segredos no relatório.
2. WHEN a execução inicia THEN ela SHALL obter respostas de sucesso para `POST /events`, `GET /events/{id}`, `POST /events/{id}/reservations`, `GET /reservations/{id}` e `DELETE /reservations/{id}` antes da massa de mil clientes.
3. WHEN a massa é executada THEN ela SHALL criar um evento dedicado com capacidade 1.000 e SHALL persistir exatamente 1.000 reservas `201`, cada uma contendo nome Faker.js e e-mail único terminado em `@example.test`.
4. WHILE a massa é executada THEN ela SHALL iniciar no máximo quatro comandos por segundo, falhar de modo explícito em resposta não-`201` e gravar um resumo sem nome, e-mail ou segredo.

**Independent Test**: testes Node verificam validação de configuração, unicidade/segurança do dado Faker e o plano de limitação de taxa; a execução AWS guarda um relatório com os cinco resultados de endpoint e a contagem 1.000.

## Requirement Traceability

| Requirement ID | Story | Phase | Status |
| --- | --- | --- | --- |
| SEED-01 | SigV4 com credenciais temporárias | Execute | Implemented |
| SEED-02 | Prova dos cinco endpoints | Execute | Implemented |
| SEED-03 | Mil clientes persistidos | Execute | Implemented |
| SEED-04 | Ritmo e relatório seguro | Execute | Implemented |

## Success Criteria

- [ ] O seeder não aceita URL não HTTPS, volume acima de 1.000 ou dados de e-mail roteáveis.
- [ ] Uma execução AWS registra cinco endpoints bem-sucedidos e `createdCustomers: 1000`.
- [ ] Nenhum segredo, nome ou e-mail de cliente aparece no relatório gerado.
