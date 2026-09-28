# Contexto confirmado

- O usuário pediu aplicação das correções para que os planos não sejam restritivos demais e autorizou reorganizar pacotes se necessário.
- O código anterior misturava responsabilidades em `application/reconciliation`, `application/outbox/OperationalDataCleaner` e `feature/reservation/expire`. O agrupamento por primeiro pacote gerava ciclos artificiais.
- A versão high-load continua apenas documentada (AD-006); não se deve atribuir a ela resultado operacional.
- Não instalar ou acionar Maven Enforcer, TFLint, Trivy ou Checkov neste trabalho.

## Deferred Ideas

- Não corrigir automaticamente achados de arquitetura AWS; cada mudança de infraestrutura exigirá tarefa própria.
