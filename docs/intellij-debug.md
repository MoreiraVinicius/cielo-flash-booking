# Executar e depurar pelo IntelliJ IDEA

O IntelliJ executa o Java da aplicacao no seu computador. O Docker Compose e usado somente para as dependencias locais: PostgreSQL, Valkey, LocalStack e Mailpit. Nao inicie `query-api`, `command-api` ou `worker` pelo Compose quando quiser depurar o Java.

## Preparacao

1. Abra o projeto pelo arquivo `pom.xml` e aguarde a importacao Maven.
2. Em **File > Project Structure**, selecione JDK 21 para o projeto e para o Maven.
3. Com o Docker Desktop ativo, abra o terminal integrado do IntelliJ e execute:

   ```powershell
   docker compose up -d postgres valkey localstack mailpit
   ```

   Isso inicia somente as dependencias que as configuracoes do IntelliJ usam em `localhost`.

## Depurar uma aplicacao

As configuracoes compartilhadas ficam em `.run/` e aparecem automaticamente no seletor de configuracao do IntelliJ depois de recarregar o projeto.

| Configuracao | Use para | Endereco |
| --- | --- | --- |
| `Query API (local Compose)` | Consultas de evento e reserva | `http://localhost:8081` |
| `Command API (local Compose)` | Criar evento, reservar e cancelar | `http://localhost:8082` |
| `Worker (local Compose)` | Outbox, expiracao e notificacoes | Sem porta HTTP publica |

1. Adicione breakpoints no codigo Java.
2. Escolha a configuracao desejada no canto superior direito.
3. Clique em **Debug** (icone de inseto), e nao em **Run**.
4. Envie a requisicao para a porta correspondente. A execucao vai parar no breakpoint dentro do processo Java iniciado pelo IntelliJ.

Para depurar o fluxo completo, inicie primeiro `Query API`, `Command API` e `Worker` em tres sessoes de Debug separadas. Todos compartilham o mesmo banco, cache, filas locais e Mailpit.

## Encerrar

Pare cada sessao pelo botao Stop do IntelliJ. Depois, se nao precisar mais das dependencias:

```powershell
docker compose down
```

## Problemas comuns

- **A configuracao nao aparece:** use **File > Reload All from Disk** e recarregue o projeto Maven.
- **Falha de conexao com PostgreSQL, Valkey ou LocalStack:** confirme que o comando de preparacao terminou e que o Docker Desktop esta em execucao.
- **Porta 8081 ou 8082 ocupada:** encerre o processo que usa a porta antes de iniciar a configuracao correspondente.
- **Classe ou dependencias nao resolvidas:** confirme que o JDK 21 esta selecionado e execute a recarga Maven.
