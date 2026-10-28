# Checagem rápida do Termux

Antes de usar o app, confirme que estes comandos funcionam no próprio Termux:

```bash
~/downloads/xmrig/build/xmrig --version
termux-battery-status
```

O campo de caminho vem preenchido com `~/downloads/xmrig/build/xmrig`. Se o binário estiver em outro lugar, informe o caminho correto na tela do X1 Miner. Se o segundo comando não existir, instale o app complementar Termux:API usado pela sua distribuição e execute `pkg install termux-api`.

O X1 Miner controla o XMRig RandomX para Monero já instalado no Termux; não copia binários para a pasta privada do app e não precisa de root. Não é compatível com pools de Dogecoin.
