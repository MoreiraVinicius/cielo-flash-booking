# Decisões de escopo

- O usuário aprovou a implementação da migração Spring Boot 4 em 2026-09-27.
- Observabilidade HTTP local é opt-in; integração AWS é planejamento, sem deploy.
- Propagação contínua por outbox/SQS/worker fica fora desta entrega.
- Runtime validado: Java 21, Spring Boot 4.0.8 e Jackson 3; o diagrama high-load continua sendo alvo não aplicado.
