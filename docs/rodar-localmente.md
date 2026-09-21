# Rodar localmente

Este guia executa a aplicacao sem AWS. O Docker Compose fornece PostgreSQL, Valkey, LocalStack (SQS) e Mailpit; a aplicacao pode rodar inteira no Compose ou ter seus tres modos Java depurados pelo IntelliJ.

## Pre-requisitos

- Docker Desktop iniciado e com `docker version` exibindo as secoes **Client** e **Server**.
- Java 21 para Maven e IntelliJ. No Windows deste projeto, o JDK usado e o Temurin 21.
- PowerShell aberto na raiz do repositorio.

Confirme rapidamente:

```powershell
docker version
.\mvnw.cmd --version
```

## Opcao 1: executar tudo pelo Compose

Esta e a forma mais curta de ter o sistema completo em execucao.

```powershell
# API de consulta, API de comandos, worker e todas as dependencias
docker compose up --build -d

# Acompanhar a inicializacao, se necessario
docker compose ps
docker compose logs -f query-api command-api worker
```

Depois que os tres processos estiverem saudaveis, execute o smoke end-to-end:

```powershell
.\scripts\compose-smoke.ps1
```

O smoke cria um evento, cria uma reserva, consulta os dois pela Query API e confirma que o Worker enviou o e-mail para o Mailpit.

Para encerrar os containers sem apagar os dados locais:

```powershell
docker compose down
```

## Opcao 2: depurar o Java pelo IntelliJ

Use esta opcao para colocar breakpoints no Java. Nao suba `query-api`, `command-api` ou `worker` pelo Compose, pois isso ocuparia as portas usadas pelo IntelliJ.

1. Abra o projeto pelo `pom.xml` e aguarde a importacao Maven.
2. Em **File > Project Structure**, selecione JDK 21 para o projeto e para o Maven.
3. Suba somente as dependencias:

   ```powershell
   docker compose up -d postgres valkey localstack mailpit
   ```

4. No seletor de configuracoes, execute em sessoes separadas:
   - `Query API (local Compose)`
   - `Command API (local Compose)`
   - `Worker (local Compose)`

As configuracoes compartilhadas ficam em `.run/`. Se nao aparecerem, use **Maven > Reload All Maven Projects** e confirme que o modulo selecionado e `flash-booking`.

Para interromper, pare as tres sessoes no IntelliJ. As dependencias podem continuar ativas para a proxima sessao de debug.

## Enderecos e credenciais locais

| Componente | Endereco | Observacao |
| --- | --- | --- |
| Query API | `http://localhost:8081` | `GET /events/{id}` e `GET /reservations/{id}` |
| Command API | `http://localhost:8082` | `POST /events`, reservas e cancelamento |
| Worker | `http://localhost:8080/actuator/health` | Usado para outbox, expiracao e notificacao |
| PostgreSQL | `localhost:15432/flash_booking` | Usuario e senha: `flash_booking` |
| Valkey | `localhost:16379` | Cache de disponibilidade |
| LocalStack | `http://localhost:14566` | Filas SQS locais |
| Mailpit | `http://localhost:8025` | Caixa de entrada dos e-mails locais |
| SMTP Mailpit | `localhost:11025` | Configurado no Worker do IntelliJ |

As portas `15432`, `16379`, `14566` e `11025` sao intencionais: evitam colisao com PostgreSQL, Redis, LocalStack e SMTP ja instalados na maquina.

## Testes

```powershell
# Testes unitarios
.\mvnw.cmd test

# Suite completa: testes unitarios e integracoes com PostgreSQL, Valkey,
# LocalStack e Mailpit via Testcontainers
.\mvnw.cmd clean verify -Pintegration
```

O Docker Desktop precisa estar saudavel para o segundo comando.

## Gerar audio para a apresentacao

No Windows, o script abaixo transforma qualquer roteiro UTF-8 em WAV. Ele requer uma voz SAPI em portugues brasileiro e grava o arquivo `.wav` ao lado do texto quando `-OutputPath` nao for informado.

```powershell
.\scripts\generate-interview-audio.ps1 -InputPath .\meu-roteiro.txt
```

Arquivos de audio gerados sao locais e nao devem ser versionados.

## Problemas comuns

### `password authentication failed for user "flash_booking"`

Quase sempre significa que a aplicacao conectou em outro PostgreSQL da maquina na porta padrao `5432`. Suba as dependencias do projeto e use a URL abaixo — as configuracoes do IntelliJ ja fazem isso:

```text
jdbc:postgresql://localhost:15432/flash_booking
```

Confirme com `docker compose ps` que o PostgreSQL esta saudavel na porta `15432`.

### `ClassNotFoundException: com.cielo.flashbooking.FlashBookingApplication`

Abra o projeto pelo `pom.xml`, recarregue o Maven e selecione o modulo `flash-booking` na configuracao. Em seguida execute `Build > Rebuild Project`.

### Docker nao responde

Abra o Docker Desktop e aguarde ate `docker version` mostrar a secao **Server**. Se estiver depurando pelo IntelliJ, suba apenas as quatro dependencias indicadas acima.

### Porta 8080, 8081 ou 8082 ocupada

Pare o processo que ocupa a porta ou nao misture as duas opcoes: Compose completo e processos Java iniciados pelo IntelliJ nao devem rodar ao mesmo tempo.
