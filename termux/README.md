# Checagem rápida do Termux

Antes de usar o app, confirme que estes comandos funcionam no próprio Termux:

```bash
~/xmrig/build/xmrig --version
termux-battery-status
```

Se o primeiro caminho não existir, localize o binário XMRig e informe o caminho correto na tela do X1 Miner. Se o segundo comando não existir, instale o app complementar Termux:API usado pela sua distribuição e execute `pkg install termux-api`.

O X1 Miner usa o executável que já está no Termux; não copia binários para a pasta privada do app e não precisa de root.
