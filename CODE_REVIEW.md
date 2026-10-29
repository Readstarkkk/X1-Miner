# Revisão técnica antes do envio

## Verificações executadas

- [x] Estrutura do projeto Android/Gradle revisada.
- [x] Manifesto, tema e recursos XML criados para Android API mínima 26.
- [x] Fluxo de compilação GitHub Actions inclui Java 17, Android SDK 35 e Gradle 8.13.
- [x] Nenhuma dependência externa de biblioteca adicionada ao módulo app.
- [x] Valores de hashrate, shares e ganhos ficam zerados/indisponíveis; não há dados minerados simulados.
- [x] A interface deixa explícito que a pool e o backend Node não estão conectados nesta versão.
- [x] Endereços de carteira e configurações são salvos por moeda usando preferências privadas locais.
- [x] Validação básica da senha Node (8+ caracteres, sem espaços/emojis).
- [x] Proteção do botão de iniciar para bateria abaixo de 20% ou temperatura reportada da bateria em 44 °C ou mais.
- [x] XMLs analisados e verificação estática de estrutura executada antes de empacotar.

## Limitação de validação

Este ambiente não tem Android SDK nem acesso de rede para baixar o SDK/Gradle, então **não foi possível executar `assembleDebug` nem instalar o APK neste ambiente**. O workflow do GitHub foi incluído para executar a compilação real e fornecer o artefato. A primeira execução do workflow é o teste decisivo de compilação.

## Itens bloqueadores para versão operacional

1. Integrar um motor real, validado e licenciado conforme necessário para cada algoritmo suportado.
2. Implementar comunicação Stratum específica por pool e validar resposta, autenticação, reconexão e shares.
3. Implementar serviço Android foreground e aplicação dos limites durante toda a sessão, não apenas ao tocar no botão.
4. Implementar e testar backend Node real com transporte criptografado, autenticação segura, autorizações e proteção contra comandos remotos perigosos.
5. Fazer testes em dispositivos reais, incluindo sensores ausentes, térmicos e interrupção de energia/rede.
