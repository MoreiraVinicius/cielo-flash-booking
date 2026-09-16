# README Visual Storytelling Validation

**Verdict:** PASS
**Date:** 2026-09-14
**Spec:** `.specs/features/readme-visual-storytelling/spec.md`
**Diff range:** `64a3ce5..9cdf4e7`
**Verifier:** independent sub-agent (author != verifier)

O conteúdo satisfaz os 9 critérios de aceitação e o gate documental discrimina uma falsificação obrigatória. Todos os gates aplicáveis passam na revisão `9cdf4e7`; o erro de whitespace anteriormente observado em `.specs/features/readme-visual-storytelling/design.md:138` foi removido e `git diff --check 64a3ce5..HEAD` retorna código 0.

---

## Task Completion

| Task | Status | Evidence |
| --- | --- | --- |
| T01--T07 | Done | `.specs/features/readme-visual-storytelling/tasks.md:35`, `:48`, `:61`, `:76`, `:89`, `:104`, `:117` registram as sete tarefas; cada bloco está `Status: Complete`. |

---

## Spec-Anchored Acceptance Criteria

| Criterion | Spec-defined outcome | `file:line` + expression / observed result | Result |
| --- | --- | --- | --- |
| README-01.1 | Identificar o Flash Booking como case independente de reservas temporárias e excluir pagamento/compra confirmada. | `README.md:5` — “reserva temporária” e “case técnico independente”; `README.md:7` — pagamento, compra confirmada e emissão fora da entrega. | PASS |
| README-01.2 | Expor exatamente cinco endpoints com finalidade e um quick start por Docker Compose. | `README.md:23-32` — `docker compose up`, smoke e `down`; `README.md:56-62` — cinco linhas com método, rota e resultado; `scripts/validate-readme.ps1:127-139` — `$actualEndpoints.Count -eq 5` e comparação com as cinco rotas esperadas. Execução: `validate-readme: PASS - 5 endpoints`. | PASS |
| README-01.3 | Mostrar visuais compactos antes de recolher C4 e sequência em deep dives opcionais. | `README.md:1`, `:68`, `:88`, `:98`, `:132` — hero e visuais narrativos no fluxo principal; `README.md:163-201` — quatro diagramas detalhados dentro de `<details>`; `scripts/validate-readme.ps1:122-124` — exige quatro pares balanceados. | PASS |
| README-02.1 | Rotular demo como 43/43 e 94 testes; high-load como planejada, não provisionada e não validada remotamente. | `README.md:15` — 43/43 e `38 + 56 = 94`; `README.md:17` — high-load planejada, não provisionada, benchmarkada ou validada remotamente; fonte canônica em `.specs/features/flash-booking-demo/validation.md:69` e `:99`. | PASS |
| README-02.2 | Preservar o domínio Java compartilhado e distinguir runtime econômico Single-AZ do alvo Multi-AZ. | `README.md:100-114` — mesma imagem Java, RDS Single-AZ versus Aurora/RDS Proxy/Valkey Multi-AZ e core preservado; `docs/images/flash-booking-architecture-evolution.svg:3`, `:67`, `:94`, `:105-106`, `:117` repetem a separação visual. | PASS |
| README-02.3 | Associar resiliência, segurança e observabilidade a sinais/evidências, sem throttling determinístico, failover high-load ou capacidade de produção. | `README.md:142-150` — cada padrão possui coluna “Sinal ou evidência”; `README.md:152` — throttling de melhor esforço e sem garantia de `429`; `README.md:134`, `:157-159` — baseline não é capacidade e high-load não comprova failover/capacidade. | PASS |
| README-03.1 | Mostrar `case -> spec -> design/ADRs -> tasks -> tests/gates -> verifier` e separar demo concluída de high-load planejada. | `README.md:88-94` — trilha completa em texto e visual; `docs/images/flash-booking-spec-driven.svg:3`, `:28`, `:74`, `:82`, `:116`, `:124` — lane demo com 94/43/43 e lane high-load com 20 tarefas sem apply, benchmark ou failover remoto. | PASS |
| README-03.2 | Publicar req/s, percentis, falhas, tamanho, proveniência e disclaimer local; o harness atual deve endereçar ao menos dois command-api; o baseline histórico não conta como prova multiprocesso. | `performance/demo/README.md:9-22` e `performance/demo/baseline.json:17-103` — VUs, 15 s, req/s, p50/p95/p99, falhas, proveniência e limitações; `compose.yaml:122`, `:134`, `:136` — perfil de concorrência, porta efêmera e duas réplicas; `performance/demo/run.ps1:32-37`, `:142-152` — exige duas URLs e exporta `COMMAND_BASE_URLS`; `performance/demo/command.js:17-18`, `:37` e `mixed.js:18-19`, `:42` — distribuem VUs e marcam o processo. `README.md:136` e `baseline.json:98-99` dizem que o baseline single-endpoint não é evidência multiprocesso. | PASS, com limitação registrada |
| README-03.3 | O gate deve falhar para asset ausente, SVG inválido, truth label ausente, contrato diferente de cinco endpoints ou baseline malformado. | `scripts/validate-readme.ps1:52-64` — truth labels; `:66-95` — referências/assets e XML; `:126-139` — cinco endpoints; `:141-180` — schema, cenários, métricas, limitações e correspondência do gráfico. Execução real passou; no sensor, trocar `43/43 critérios` por `42/43 critérios` produziu código 1 e “README truth contract is missing '43/43 critérios'”. | PASS |

**Spec-anchored status:** 9/9 critérios correspondem ao resultado preciso definido pela spec; 0 spec-precision gaps.

---

## Discrimination Sensor

| Mutation | Scratch location | Expected failure | Observed result | Killed? |
| --- | --- | --- | --- | --- |
| `README.md:15` — `43/43 critérios` -> `42/43 critérios` | Worktree temporária destacada em `%TEMP%`, nunca na árvore real | O contrato de verdade deve rejeitar o fato adulterado. | `scripts/validate-readme.ps1` retornou código 1: `README truth contract is missing '43/43 critérios'`. | Killed |

**Sensor depth:** lightweight, 1 targeted documentation mutation.
**Isolation:** `git status --porcelain=v1 --untracked-files=all` estava vazio antes e depois; a worktree do sensor foi removida e o caminho temporário deixou de existir.
**Result:** 1/1 killed, PASS.

---

## Gate Summary

| Gate | Result | Observed evidence |
| --- | --- | --- |
| README contract | PASS | Código 0; 5 endpoints, 10 imagens, 37 referências locais e 3 cenários. |
| Spec structure | PASS | `validate_spec.py`: 0 errors, 0 warnings. |
| Task structure | PASS with warnings | `validate_tasks.py`: 0 errors, 5 granularity warnings em T01, T03, T04, T05 e T07. |
| Diff whitespace, feature range | PASS | `git diff --check 64a3ce5..HEAD`: código 0 em `9cdf4e7`. |
| Working-tree whitespace | PASS | `git diff --check`: código 0. |
| SVG XML | PASS | 10 arquivos em `docs/images/*.svg` carregados como XML e com raiz `svg`. |
| Compose static config | PASS with environment warning | `docker compose --profile concurrency config --quiet`: código 0; leitura de `C:\Users\vinic\.docker\config.json` negada pelo sandbox, sem invalidar o modelo Compose. Nenhum container foi iniciado. |
| k6 inspect | PASS | `query.js`: 5 VUs/15 s/p95 < 500 ms; `command.js`: 3 VUs/15 s/p95 < 750 ms; `mixed.js`: 4 VUs/15 s/p95 < 750 ms; todos exigem falhas < 1%. |
| Java diff | PASS | `git diff --name-only 64a3ce5..HEAD -- src/main src/test` retornou vazio. |
| Surefire atual | PASS, evidence audit only | 11 XMLs, 38 testes, 0 failures, 0 errors, 0 skipped; timestamps 2026-09-14 12:50:29--12:50:30 -03:00. |
| Failsafe histórico | PASS, not rerun | 14 XMLs, 56 testes, 0 failures, 0 errors, 0 skipped; timestamps 2026-09-11 15:29:59--15:31:56 -03:00. |

---

## Edge Cases and Limitations

- Docker Desktop estava indisponível. Nenhuma integração, carga ou prova multiprocesso nova foi executada.
- O baseline versionado continua sendo o snapshot histórico local single-endpoint. `performance/demo/baseline.json:91-103` registra a tentativa bloqueada e declara que o baseline não é evidência multiprocesso.
- A configuração estática prova que o harness atual exige dois processos explícitos; ela não prova distribuição runtime, capacidade, failover ou comportamento high-load.
- O cenário misto é explicitamente agregado em `baseline.json:55-70` e `performance/demo/README.md:15`.
- O snapshot final de locks não é uma série temporal, conforme `baseline.json:79-82`, `:101`.

---

## Code Quality and Scope

| Check | Status |
| --- | --- |
| Nenhuma mudança Java/Spring em `src/main` ou `src/test` | PASS |
| Mudanças limitadas a documentação, evidência, harness e gate da feature | PASS |
| Contrato do projeto e decisões AD-006/AD-016 preservados | PASS |
| Nenhuma métrica nova alegada sem execução | PASS |
| Assertions documentais ancoradas nos resultados da spec | PASS |
| Gate obrigatório sem erro de whitespace no range | PASS |

---

## Ranked Gaps

Nenhum gap funcional, estrutural, de precisão da spec ou de discriminação permanece. O erro de whitespace observado na primeira rodada foi corrigido em `9cdf4e7` e o gate correspondente foi reexecutado com sucesso.

---

## Summary

**Overall:** PASS. Os 9/9 critérios de aceitação passam, o sensor matou 1/1 mutação, os validadores documentais/estruturais e os checks estáticos de Compose, k6, SVG e diff passam, e os relatórios atuais mostram 38/38 unitários. O working tree real contém somente este `validation.md` não versionado.

## Evidence reconciliation — 2026-09-15

O `43/43` e os 94 testes continuam rotulados no README como o último baseline integral, não como uma reexecução após mudanças. A suíte rápida atual passou com 46/46; a árvore atual contém 57 ITs compilados, mas os novos cenários PostgreSQL de idempotência não foram executados. `scripts/validate-readme.ps1` passou exigindo esses rótulos distintos. O relatório de idempotência em `.specs/features/flash-booking-demo/validation.md` registra a evidência e os gaps de runtime. Esta reconciliação atualiza a verdade documental; não reescreve a execução histórica acima.

## Verificação independente de T08 / README-04 — 2026-09-16

**Verdict:** PASS. A mudança `f0c450c` visualiza fatos já observados na demo AWS e não os transforma em benchmark DDoS, admissão determinística ou capacidade sustentável.

**Diff verificado:** `f0c450c^..f0c450c`.

### Critérios ancorados na spec

| Critério | Resultado definido | Evidência `file:line` | Resultado |
| --- | --- | --- | --- |
| README-04.1 | GET assinado: 80 chamadas iniciadas em 834 ms, `80 × 503`, nenhum `429` e segunda tentativa bloqueada por WAF com `403`. | `.specs/features/readme-visual-storytelling/spec.md:84`; `.specs/features/flash-booking-demo/validation.md:58`; `README.md:185`; `docs/images/flash-booking-edge-burst.svg:42-54`; `scripts/validate-readme.ps1:81,404-408`. | PASS |
| README-04.2 | POST seguro assinado: 20 chamadas, `20 × 400`, nenhum `429`; DELETE omitido por exigir reserva persistida e causar efeito de estado. | `.specs/features/readme-visual-storytelling/spec.md:85`; `.specs/features/flash-booking-demo/validation.md:59`; `README.md:185`; `docs/images/flash-booking-edge-burst.svg:60-68`; `scripts/validate-readme.ps1:82,409-411`. | PASS |
| README-04.3 | Targets de WAF/API Gateway como melhor esforço; evidência histórica, não teste DDoS nem capacidade sustentável. | `.specs/features/readme-visual-storytelling/spec.md:86`; `docs/images/flash-booking-edge-burst.svg:16-20,26-36,72-74`; `README.md:183-185`; `scripts/validate-readme.ps1:412-419`. | PASS |

**Spec-anchored status:** 3/3 critérios correspondem aos resultados precisos definidos; 0 gaps de precisão.

### Gates

| Gate | Resultado |
| --- | --- |
| `scripts/validate-readme.ps1` | PASS — 5 endpoints, 14 imagens, 42 referências locais e 3 cenários de desempenho. |
| `validate_spec.py` | PASS — 0 erros, 0 avisos. |
| `validate_tasks.py` | PASS — 0 erros; 6 avisos de granularidade legados, incluindo T08 por cobrir documentação, SVG e gate no mesmo commit. |
| XML do SVG e `git diff --check f0c450c^ f0c450c` | PASS. |

### Discrimination sensor

| Mutação isolada | Resultado esperado | Resultado observado | Killed? |
| --- | --- | --- | --- |
| Em uma worktree temporária destacada de `f0c450c`, `README.md` trocou `80 × 503` por `79 × 503`. | O contrato de verdade deve rejeitar a distribuição GET adulterada. | `validate-readme.ps1` falhou com `README truth contract is missing '80 GETs assinados iniciados em 834 ms retornaram \`80 × 503\`'`. | PASS |

**Isolamento:** a worktree temporária foi removida. O status da árvore real permaneceu igual antes e depois do sensor: somente o arquivo preexistente sob `.tmp/outbox-scale-sensor-9f2666d/` não rastreado.

### Qualidade e escopo

- [x] O novo SVG é acessível e XML válido; não contém raster nem `foreignObject`.
- [x] A comparação entre targets e observações permanece explícita, incluindo o rótulo `MELHOR ESFORÇO` e a ausência de `429` determinístico.
- [x] O visual de trilha agora classifica `94`/`43/43` como `baseline integral histórico` em `docs/images/flash-booking-spec-driven.svg:76`.
- [x] Não há mudança Java/Spring, execução de carga nova ou alegação nova de capacidade.

**Gaps:** nenhum. **Próximo passo:** concluir o gate de estado e registrar o fechamento da feature.
