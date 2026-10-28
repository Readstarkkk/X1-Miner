# X1 Monitor — Android thermal monitor

Esta versão é uma reconstrução simplificada do projeto original. Ela monitora a temperatura reportada pela bateria e, quando disponível, o estado térmico do Android. **Não inicia, configura nem controla mineradores ou outros processos.** Essa mudança é intencional: executar tarefas de CPU contínuas em um telefone pode gerar aquecimento e desgaste.

## Recursos

- Interface Android escura em roxo e laranja.
- Leitura da temperatura da bateria quando o aparelho a disponibiliza.
- Leitura do estado térmico Android em API 29 ou superior.
- Monitoramento contínuo opcional em serviço foreground com notificação.
- Avisos visuais a partir de 42 °C, alertas a partir de 45 °C e mensagens para estados térmicos severos.
- Sem dependências externas de UI e sem acesso a Termux, rede, carteira ou pool.

Os sensores variam por aparelho. A temperatura da bateria não representa a temperatura exata da CPU e pode não estar disponível. O aplicativo não substitui os mecanismos térmicos do Android.

## Requisitos técnicos

- Android 8.0 (API 26) ou superior.
- Java 17 para compilar.
- Android SDK Platform 35 e Build Tools 35.0.0.
- Android Gradle Plugin 8.7.3 com Gradle 8.9.

## Compilar pelo GitHub Actions

O workflow fica em `.github/workflows/android.yml`. Envie o projeto para `main` ou `master`, ou abra a aba **Actions** e execute **Build X1 Monitor APK** manualmente. O workflow configura explicitamente `platform-tools`, evitando o pacote legado `tools` que causou a falha anterior. Depois de uma compilação bem-sucedida, o arquivo `X1-Monitor-debug-apk` estará na seção **Artifacts**.

O artefato é um APK de depuração. Ainda precisa ser instalado e testado no aparelho antes de ser considerado validado. Não há como garantir ausência absoluta de bugs sem testar o aplicativo no dispositivo alvo.
