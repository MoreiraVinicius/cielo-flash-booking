# Desafio Técnico

## Engenheiro de Backend Sênior / Especialista

## Reserva de Ingressos (Flash Booking)

## Contexto

Você foi contratado para construir o núcleo de um sistema de reserva de ingressos. Os eventos possuem capacidade limitada e operam em modelo flash sale. Após a entrega haverá sessão de code review e apresentação das decisões arquiteturais.

## Requisitos Funcionais

| Método | Rota | Descrição |
| --- | --- | --- |
| POST | `/events` | Criar evento |
| GET | `/events/:id` | Consultar disponibilidade |
| POST | `/events/:id/reservations` | Reservar ingressos |
| GET | `/reservations/:id` | Consultar reserva |
| DELETE | `/reservations/:id` | Cancelar reserva |

## Requisitos Não Funcionais

- Múltiplas instâncias simultâneas da API
- Nunca permitir oversell
- Expiração automática de reservas pendentes
- Idempotência
- Consistência eventual para disponibilidade
- Tratamento explícito de erros

## Restrições Técnicas

Linguagem, framework e banco de dados livres. Docker Compose, testes automatizados e README obrigatórios.

## Entrega

Publicar em repositório GitHub e documentar instruções, decisões arquiteturais, trade-offs e evoluções futuras.

## Orientações para a avaliação

- É esperado que você utilize ferramentas de IA durante a elaboração da solução. Estamos construindo uma cultura IA first e isso fará parte da sua avaliação.
- Buscamos uma entrega completa e funcional, contemplando a implementação da proposta.
- Durante o Case Review, será importante conseguir explicar seu racional, as decisões tomadas e o caminho percorrido para chegar à solução.
- A avaliação será focada principalmente na qualidade do código desenvolvido, portanto não é necessário preparar apresentações ou materiais complementares sem relação direta com a implementação.

## Preferências técnicas da vaga

Ao executar este case, dê preferência aos itens abaixo, pois são os principais focos técnicos informados para a vaga de Especialista em Desenvolvimento Backend da Cielo:

1. **Java e Spring Boot** - priorizar o ecossistema Java, com Spring Boot, Spring Data e Spring Security.
2. **APIs REST** - projetar e implementar as rotas como APIs REST claras e consistentes.
3. **Arquitetura de microsserviços e sistemas distribuídos** - tratar corretamente os aspectos de concorrência, escalabilidade, resiliência, disponibilidade e consistência distribuída exigidos pelo case.
4. **Qualidade e arquitetura de código** - aplicar SOLID, Clean Code, Clean Architecture e práticas de code review.
5. **Testes automatizados** - incluir automação de testes unitários e de integração, com cobertura relevante dos cenários de negócio e de concorrência.
6. **Segurança** - considerar Spring Security e a identificação, revisão e mitigação de vulnerabilidades no código e na arquitetura.
7. **Entrega e operação** - usar Docker/Docker Compose e contemplar CI/CD; demonstrar que a solução pode evoluir para ambientes com Kubernetes e controles de segurança em cloud, quando aplicável.
8. **Modelagem de dados e algoritmos** - justificar as decisões de persistência, integridade e desempenho de acordo com a reserva de ingressos em flash sale.

Também são relevantes para o contexto da vaga: metodologias ágeis, visão de negócio de meios de pagamento, ferramentas de gestão como Jira e Azure DevOps, além de documentação das decisões técnicas, trade-offs e riscos.

Fonte: [descrição da vaga de Especialista em Desenvolvimento BackEnd da Cielo](https://www.glassdoor.com.br/job-listing/especialista-em-desenvolvimento-backend-cielo-pagamentos-JV_KO0%2C39_KE40%2C56.htm?jl=1010179986345).
