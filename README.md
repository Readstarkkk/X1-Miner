# X1 Miner — projeto Android

Interface Android em laranja e roxo para controlar o **XMRig RandomX (Monero) que já está instalado no Termux**. O app não inventa hashrate: o log mostrado pelo XMRig é a fonte dos valores reais.

## Importante: o que este projeto é (e não é)

- É o código-fonte de um app Android, com seletor de threads, configuração de pool/carteira, botão iniciar/parar e proteção térmica.
- Ele **usa um XMRig ARM64 já instalado e funcional dentro do Termux**. Não inclui o executável XMRig nem uma APK pronta.
- O app começa com um limite conservador de threads e a proteção de 45 °C lê a **temperatura da bateria**, não uma temperatura exata do processador. Também interrompe a mineração se o Android informar estado térmico `SEVERE`.
- Para reduzir o risco de aquecimento, o seletor limita o miner a no máximo metade dos núcleos lógicos detectados; o valor inicial usa menos threads. Isso não corresponde a uma porcentagem garantida da CPU, pois o Android pode controlar a frequência e o agendamento.
- Se a ferramenta do Termux não conseguir ler a temperatura da bateria, o monitor dentro do Termux para a mineração por segurança.

## Requisitos

1. Android 8.0 ou superior.
2. Termux instalado e funcionando.
3. XMRig compilado para ARM64/Android dentro do Termux.
4. O app complementar Termux:API e o pacote `termux-api`, para que o monitor independente consiga ler a temperatura.
5. Uma IDE Android/Gradle com Android SDK 35 para gerar a APK.

O campo de caminho começa com `~/downloads/xmrig/build/xmrig`, porque esse foi o local usado no projeto. No Termux, confira o caminho:

```bash
~/downloads/xmrig/build/xmrig --version
```

Se seu executável estiver em outro lugar, altere o campo **CAMINHO DO XMRIG NO TERMUX** dentro do X1 Miner.

Instale/configure o Termux:API conforme a distribuição usada e, dentro do Termux, execute:

```bash
pkg install termux-api
termux-battery-status
```

O comando deve retornar JSON com um campo `temperature` maior que zero. O app não inicia um miner sem o comando e a leitura térmica disponíveis.

## Permitir que o X1 Miner controle o Termux

A integração oficial do Termux exige que você permita comandos externos. Essa permissão é poderosa: **só dê acesso a apps cujo código você confia**.

1. No Termux, edite `~/.termux/termux.properties` e configure:

   ```properties
   allow-external-apps = true
   ```

2. Recarregue as configurações:

   ```bash
   termux-reload-settings
   ```

3. Nas configurações do Android, abra **Apps → X1 Miner → Permissões → permissões adicionais** e permita **Executar comandos no Termux** (`RUN_COMMAND`). O nome pode variar por versão.

O app envia ao Termux apenas comandos para iniciar o XMRig configurado ou encerrar o PID salvo em `~/.x1miner/miner.pid`.

## Compilar

Abra esta pasta como projeto Gradle em uma IDE Android que tenha o SDK 35 instalado e gere a variante `debug` ou `release`. Dependências do projeto são as bibliotecas padrão do Android Gradle Plugin; não há dependências de interface externas.

Este ambiente não tem Android SDK/Gradle instalados, então não consegui compilar ou testar a APK aqui. O arquivo entregue é o **projeto-fonte**, não uma APK compilada.

## Usar

1. Abra o X1 Miner e preencha `pool host:porta`, endereço da carteira Monero, worker, senha e TLS, conforme a configuração da sua pool.
2. Ajuste o número de threads. O seletor de threads tem um limite conservador para reduzir o aquecimento.
3. Pressione **INICIAR MINERAÇÃO**. A tela informa que o comando foi enviado ao Termux; isso não significa que o miner iniciou ou que a pool aceitou shares. Pressione **VER LOG REAL DO XMRIG NO TERMUX** para ler as últimas mensagens e confirmar conexão, hashrate e shares aceitas.
4. Para parar manualmente, pressione **PARAR**.

Log do miner:

```bash
tail -n 80 ~/.x1miner/miner.log
```

## Proteção térmica

- O app bloqueia o início se não conseguir ler a temperatura da bateria ou se ela estiver em **42 °C ou mais**. Ao ler bateria a **45,0 °C ou mais**, o monitor do próprio Termux envia `SIGTERM` ao XMRig, tenta encerrá-lo e registra o motivo no log.
- Se a temperatura estiver indisponível/ilegível, o monitor para o miner em vez de continuar sem proteção.
- Enquanto o serviço Android está ativo, ele também para o miner se a temperatura da bateria chegar a 45 °C ou se o Android reportar estado `SEVERE`.
- Ao ocorrer o limite, o X1 Miner envia o comando de parada e mostra o estado de segurança na tela/notificação; a tela não fecha automaticamente.

Não execute mineração contínua sem supervisão; a temperatura da bateria não representa todos os sensores internos, e a proteção do sistema ainda pode limitar ou desligar o telefone.

## Referências

- XMRig (código e documentação): https://github.com/xmrig/xmrig — o projeto upstream está sob GPL-3.0.
- API oficial de comandos externos do Termux: https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
- API térmica Android: https://developer.android.com/reference/android/os/PowerManager

## Gerar APK automaticamente no GitHub

O repositório inclui um workflow em `.github/workflows/android.yml`. Depois de enviar os arquivos para a branch `main` (ou `master`), abra a aba **Actions**, escolha **Build X1 Miner APK** e, se necessário, use **Run workflow**. Quando o build terminar com sucesso, abra a execução e baixe o artefato **X1-Miner-debug-apk**. Esse artefato é uma APK de depuração, não uma versão assinada para publicação.

O primeiro build ainda precisa ser validado no ambiente do GitHub. A APK deve ser testada no aparelho antes de qualquer uso de mineração real.
