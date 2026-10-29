# X1 Miner — Android project

Aplicativo Android nativo em Java, com tema preto/dourado/laranja e quatro abas:

1. **Minerar** — seleção de moeda, carteira, pool/porta, algoritmo esperado e leitura disponível de bateria/temperatura.
2. **Gráfico** — estatísticas reais ficam sem dados até integrar um motor de mineração.
3. **Configurações** — preferências persistidas localmente para threads, perfil, unidade/limite de hashrate e proteções.
4. **Node** — telas de criação/entrada com validação básica de formulário; conexão remota ainda depende de backend.

## Estado desta versão

Esta é uma **base compilável da interface (0.1.0-preview)**, não um minerador operacional. Ela **não abre conexões de pool, não calcula hashrate real, não envia shares e não estima ganhos**. O botão de iniciar valida alguns requisitos e informa que o motor não está integrado. O formulário Node não cria uma Node remota.

Para uma versão que minere de verdade, ainda é necessário integrar e testar motores compatíveis por algoritmo, protocolo de pool/Stratum, serviço em primeiro plano, tratamento de falhas, proteção térmica baseada em sensores apropriados e um backend Node com autenticação e comunicação criptografada. A interface não deve ser tratada como prova de rentabilidade ou de desempenho.

## Compilar no GitHub usando o Termux para enviar o código

O workflow `.github/workflows/android-build.yml` compila o APK em um runner do GitHub quando você envia um commit ou inicia manualmente o workflow. Ele usa Java 17, Android SDK 35 e Gradle 8.13.

1. Baixe e extraia este projeto no celular.
2. Dentro da pasta `x1miner_project`, configure o repositório remoto correto e envie os arquivos:

```sh
git init
git branch -M main
git add .
git commit -m "Add X1 Miner Android project"
git remote add origin https://github.com/SEU-USUARIO/SEU-REPOSITORIO.git
git push -u origin main
```

Se você já tem um repositório com arquivos importantes, **não use `git init` nem substitua o projeto sem conferir primeiro**. Copie estes arquivos para uma branch separada ou faça backup antes.

3. No GitHub, abra a aba **Actions**, selecione **Build X1 Miner Android APK** e abra a execução mais recente.
4. Quando terminar com sucesso, baixe o artefato **X1-Miner-debug**. Dentro dele estará `app-debug.apk`.

O build também inicia automaticamente em cada `push` e `pull_request`.

## Compilação local

O script `gradlew` incluído baixa a distribuição oficial Gradle 8.13 na primeira execução. A compilação local também exige um JDK compatível e Android SDK instalado. Para este fluxo, o recomendado é deixar o GitHub Actions compilar o APK.

## Privacidade e segurança

- As preferências e endereços de carteira inseridos na tela Minerar são armazenados localmente nas preferências privadas do aplicativo.
- Não coloque senhas de carteiras em campos de Node.
- O Node real não está ativo e nenhuma senha é enviada a um servidor nesta versão.
- O teto de `1 TH/s` exibido na configuração é apenas um limite de interface, não uma capacidade prometida do aparelho.
