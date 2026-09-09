# Pesquisa: skills e práticas para Spring Boot, Spring Data e Spring Security

Este levantamento considera as skills disponíveis nesta sessão e documentação primária do projeto Spring. Não instalou skills nem alterou código. O projeto usa Spring Boot `3.5.0`, Java 21 e Spring Data JPA, conforme `pom.xml`; portanto, a documentação Boot 3.5 é a referência de compatibilidade para a aplicação atual.

## Conclusão

A skill atual que cobre, de forma integrada, **Spring Boot, Spring Data/JPA e Spring Security** é `java-spring-engineering`. Ela é a escolha principal para implementação, revisão e manutenção de código Java/Spring neste repositório.

Como complemento de persistência, há `design-postgres-tables`, apropriada para o desenho e a evolução do schema PostgreSQL que sustenta o Spring Data JPA. Não há uma skill separada e dedicada exclusivamente a Spring Security no catálogo atual; as regras de segurança estão incluídas em `java-spring-engineering`.

| Área | Skill disponível | Quando aplicar | Cobertura relevante |
| --- | --- | --- | --- |
| Spring Boot | `java-spring-engineering` | Qualquer mudança ou revisão de código Java/Spring | Auto-configuração, propriedades tipadas, injeção, MVC/WebFlux, configurações, timeouts, testes e operações. |
| Spring Data/JPA | `java-spring-engineering` | Repositórios, transações, JPA, queries, locking e migrations | Limites transacionais, prevenção de N+1, paginação, migrações, concorrência e integridade. |
| Banco PostgreSQL | `design-postgres-tables` | Tabelas, constraints, índices, migrations ou desempenho do banco | Modelo relacional, tipos, chaves, integridade e índices; complementar ao uso de Spring Data, não substitui a skill Java/Spring. |
| Spring Security | `java-spring-engineering` | Autenticação, autorização, segredos, entrada não confiável, Actuator e exposição HTTP | Deny-by-default, autorização por recurso, CSRF, logs, segredos e superfícies de gestão. |

As descrições acima são do catálogo local de skills desta sessão e de seus arquivos `SKILL.md`; não são dependências do projeto. A skill `java-spring-engineering` também direciona regras detalhadas de Spring/Data e Security em suas referências locais.

## Práticas que a skill principal deve reforçar

### Spring Boot

- Manter a classe principal em um pacote-raiz do aplicativo, fora do pacote padrão. O Boot usa esse pacote como base de busca para componentes e entidades JPA; isso limita o scanning ao projeto. [Documentação de estrutura de código](https://docs.spring.io/spring-boot/3.5/reference/using/structuring-your-code.html)
- Preferir starters e o gerenciamento de dependências/BOM do Boot; não sobrescrever versões individuais sem uma necessidade de compatibilidade documentada. A auto-configuração é deliberadamente não invasiva e os beans da aplicação prevalecem; tornar uma configuração explícita somente para alterar um comportamento conhecido. [Sistemas de build](https://docs.spring.io/spring-boot/reference/using/build-systems.html) e [auto-configuração](https://docs.spring.io/spring-boot/reference/using/auto-configuration.html)
- Externalizar configuração por ambiente e agrupar opções em `@ConfigurationProperties` tipado e validado. A precedência entre fontes de configuração é significativa; evitar misturar YAML e `.properties` na mesma aplicação, pois a documentação recomenda um formato único. [Configuração externalizada](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
- Expor o mínimo de Actuator. Endpoints podem revelar propriedades, beans, mappings e logs; quando adequado, usar acesso opt-in (`management.endpoints.access.default=none`) e liberar individualmente apenas o necessário. [Controle de acesso aos endpoints](https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html)
- Preferir testes de slice, como `@WebMvcTest` e `@DataJpaTest`, quando o recorte bastar; reservar `@SpringBootTest` para integração e usar `RANDOM_PORT` para testes end-to-end. [Testes de aplicações Spring Boot](https://docs.spring.io/spring-boot/reference/testing/spring-boot-applications.html)

### Spring Data/JPA

- Declarar a fronteira de transação no método público do serviço que representa a unidade de trabalho, especialmente quando coordena múltiplos repositórios. A documentação recomenda limites de transação no início da unidade de trabalho. [Transacionalidade no Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
- Usar `readOnly = true` em consultas como dica de desempenho, não como mecanismo de segurança ou garantia de que não haverá escrita. Queries declaradas não recebem configuração transacional por padrão; operações modificadoras precisam de configuração apropriada. [Transacionalidade no Spring Data JPA](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html)
- Limitar leituras de coleções com `Pageable`/paginação (ou scrolling para grandes volumes), em vez de tratar `findAll()` como operação segura para tabelas que crescem. `Page` executa uma contagem adicional; se a navegação só avança, avaliar `Slice`. [Conceitos de repositórios](https://docs.spring.io/spring-data/jpa/reference/repositories/core-concepts.html) e [detalhes de query methods](https://docs.spring.io/spring-data/jpa/reference/repositories/query-methods-details.html)
- Buscar somente os dados necessários e prevenir N+1: projections, consultas explícitas, `fetch join` ou entity graphs devem ser decididos pelos padrões de acesso. Para concorrência de atualização, preferir locking otimista com `@Version` quando houver risco real de perda de alteração.
- Versionar o schema por Flyway, manter migrations aplicadas imutáveis e testar a migração desde um estado anterior suportado. Para tabelas e índices PostgreSQL, aplicar também `design-postgres-tables`.

### Spring Security

- Configurar regras HTTP explícitas, específicas antes das genéricas, e encerrar com negação por padrão (`anyRequest().denyAll()`), salvo se a regra de produto exigir que todo o restante seja autenticado. As regras são avaliadas em ordem e apenas a primeira que corresponde é aplicada. Preferir `permitAll` a ignorar caminhos, preservando os filtros e cabeçalhos de segurança. [Autorização de requisições HTTP](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- Testar acesso anônimo, autoridade insuficiente e acesso autorizado. A documentação mostra testes com `@WithMockUser` para validar a política efetiva. [Testes de autorização](https://docs.spring.io/spring-security/reference/servlet/authorization/authorize-http-requests.html)
- Proteger também métodos de serviço quando a decisão depende do recurso ou de parâmetros; habilitar `@EnableMethodSecurity`, pois não é habilitado automaticamente pelo starter. [Segurança em métodos](https://docs.spring.io/spring-security/reference/servlet/authorization/method-security.html)
- Manter CSRF compatível com o tipo de cliente e mecanismo de autenticação; não o desabilitar por conveniência. Para que a proteção funcione, `GET`, `HEAD`, `OPTIONS` e `TRACE` devem ser somente leitura. [Proteção CSRF](https://docs.spring.io/spring-security/reference/features/exploits/csrf.html)
- Configurar CORS estritamente e antes da cadeia de segurança: uma preflight não transporta cookies, portanto não deve ser bloqueada como se fosse uma sessão autenticada. [Integração CORS](https://docs.spring.io/spring-security/reference/servlet/integrations/cors.html)
- Armazenar senhas com um `PasswordEncoder` e função adaptativa de mão única da plataforma, nunca em texto puro ou com algoritmo caseiro; calibrar o fator de trabalho para cerca de um segundo no hardware de produção. [PasswordEncoder](https://docs.spring.io/spring-security/reference/servlet/authentication/passwords/password-encoder.html) e [armazenamento de senha](https://docs.spring.io/spring-security/reference/features/authentication/password-storage.html)
- Derivar identidade e tenant da autenticação verificada, não de campos mutáveis do request; aplicar autorização no servidor e no recurso quando a posse do objeto importar. Nunca registrar tokens, senhas, cabeçalhos de autorização ou dados pessoais desnecessários.

## Uso prático no repositório

Para mudanças usuais de API, serviço, repositório ou configuração, aplicar `java-spring-engineering` como skill-base. Se a alteração envolver DDL, índices, constraints ou migrações PostgreSQL, combiná-la com `design-postgres-tables`. Para recursos de login/autorização, não há outra skill obrigatória a instalar: usar `java-spring-engineering` e validar a decisão contra a documentação oficial de Spring Security compatível com a versão efetivamente adotada.
