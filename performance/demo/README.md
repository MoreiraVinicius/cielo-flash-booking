# Baseline local da demo

Este benchmark responde uma pergunta estreita: **como a demo se comporta em snapshots locais curtos?** Ele não define SLO, capacidade sustentável, desempenho da AWS ou comportamento da arquitetura high-load.

## Resultado canônico

O arquivo versionado [`baseline.json`](baseline.json) normaliza os resumos k6 preservados localmente em 2026-09-09. Os três cenários usaram VUs constantes durante 15 segundos.

| Cenário | Carga | Requisições | Vazão | p50 | p95 | p99 | Falhas HTTP |
| --- | ---: | ---: | ---: | ---: | ---: | ---: | ---: |
| Consultas | 5 VUs | 23.809 | **1.538,86 req/s** | 2,40 ms | 6,73 ms | 11,57 ms | 0% |
| Reservas | 3 VUs | 4.018 | **267,30 req/s** | 10,39 ms | 16,87 ms | 24,82 ms | 0% |
| Misto | 4 VUs | 12.655 | **841,65 req/s** | 3,45 ms | 10,57 ms | 15,44 ms | 0% |

No cenário misto, cada iteração faz um GET e uma em cada três também faz um POST; seus percentis agregam leitura e escrita. Depois da suíte, Valkey tinha 28.530 hits e 4.767 misses (**85,68%**). O snapshot final do PostgreSQL mostrou 51 conexões e zero sessões aguardando lock naquele instante — isso não é uma série temporal.

## Proveniência e limite multiprocesso

- O workload corresponde ao commit `9f3a81a`; os arquivos ignorados foram concluídos entre 17:46 e 17:47 de 2026-09-09. O HEAD exato da execução não foi registrado, por isso não é inferido como fato.
- O runner histórico iniciava duas réplicas extras, mas o k6 chamava somente `localhost:8082`, porta do serviço `command-api` principal. Portanto, **esse baseline não prova distribuição multiprocesso**.
- Desde `dd2fdaf`, cada réplica publica uma porta efêmera; `run.ps1` descobre ao menos duas URLs e distribui os VUs explicitamente entre elas. A publicação falha se encontrar menos de dois processos ou se um threshold falhar.
- Uma reexecução foi tentada em 2026-09-14, mas o Docker Desktop falhou antes do workload ao inicializar um socket local. Nenhum número novo substituiu o baseline.

O oversell zero é comprovado pelos testes de integração e concorrência, não pela vazão acima. Da mesma forma, o primeiro gargalo provável é a linha de inventário disputada no writer PostgreSQL, mas sua saturação ainda exige um teste progressivo e telemetria durante toda a janela.

## Reexecutar

Pré-requisitos: Docker Desktop saudável, k6 0.48+ e worktree Git limpa para publicação canônica.

```powershell
# Execução descartável; grava somente em performance/demo/results/
.\performance\demo\run.ps1

# Publica performance/demo/baseline.json somente após todos os gates
.\performance\demo\run.ps1 -PublishBaseline
```

O runner:

1. constrói e inicia a stack com duas réplicas adicionais de comandos;
2. descobre e testa a saúde das duas portas efêmeras;
3. zera os contadores do Valkey;
4. executa consultas, reservas e tráfego misto;
5. coleta resumos k6, contadores de cache e snapshot final do PostgreSQL;
6. grava ambiente, commit, imagem, limitações e resultados sanitizados;
7. sempre encerra a stack.

Os artefatos brutos permanecem em `performance/demo/results/` e não entram no Git. Depois de uma publicação válida, o gráfico do README deve ser atualizado a partir do JSON canônico; o gate documental impede divergência dos valores exibidos.
