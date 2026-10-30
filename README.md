X1 Miner

X1 Miner é um projeto Android em desenvolvimento para mineração de criptomoedas, com interface preta, dourada e laranja.

Funcionalidades

- Minerar: seleção de moeda, carteira, pool, algoritmo e controles de sessão.
- Gráfico: acompanhamento de hashrate, tempo de mineração, hashes e shares.
- Configurações: opções de desempenho, threads, notificações e proteções do dispositivo.
- Node: interface para criar ou entrar em um grupo de dispositivos.

Tecnologias

- Android
- Java
- Gradle
- GitHub Actions para compilação

Compilação

1. Abra a aba Actions deste repositório no GitHub.
2. Selecione o workflow de compilação Android.
3. Execute o workflow, se necessário.
4. Após uma compilação bem-sucedida, baixe o APK na seção de artefatos da execução.

Bitcoin (BTC) — laboratório SHA-256d

A opção Bitcoin nesta atualização executa somente uma validação offline de um cabeçalho de 80 bytes, usando o bloco gênese como vetor conhecido. Ela não faz busca de nonce, mineração contínua, conexão Stratum, envio de shares ou estimativa de ganhos. O botão de mineração fica desativado para BTC.

Estado do projeto

O projeto está em desenvolvimento. O motor de mineração real, a conexão com pools e o backend de Node precisam ser implementados e testados antes de serem considerados funcionais.

Os resultados apresentados pela interface não devem ser tratados como ganhos reais sem integração e validação com uma pool.

Segurança

- Nunca compartilhe chaves privadas ou frases de recuperação de carteiras.
- Não coloque senhas ou tokens do GitHub no código.
- Teste os limites de temperatura e bateria antes de qualquer sessão prolongada.

Licença

Consulte o arquivo "LICENSE" para conhecer os termos de utilização e distribuição.