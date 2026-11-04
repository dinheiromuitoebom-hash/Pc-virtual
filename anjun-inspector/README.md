# Anjun Auto Inspector v0.1.1

Aplicativo Android 8.0+ para observação local e passiva da estrutura de telas. Registra somente tipos e contagens de elementos, sem textos pessoais, fotos ou transmissão pela internet. Não realiza cliques nem baixas.

## Instalação

Baixe o arquivo `AnjunAutoInspector-v0.1.1.apk` na release mais recente do Inspector. É um APK experimental com assinatura de debug. Instale, abra o app, habilite o serviço nas configurações de acessibilidade e navegue manualmente pela Anjun. Depois volte ao Inspector para compartilhar ou apagar o relatório.

## Estado desta versão

- Compilação com SDK 35, JDK 17 e Gradle 8.9.
- Workflow instala pacotes Android explicitamente, verifica a assinatura do APK, calcula SHA-256 e publica arquivo direto em Releases.
- Quebras de linha corrigidas; eventos do próprio Inspector ignorados; pacote da janela validado contra o evento.
- Filtro atual: nome do pacote contém `anjun`. Compatibilidade com a versão instalada da Anjun ainda precisa de validação no aparelho.
- Painel flutuante, Roteia e navegação automática ainda não estão implementados.

## Compilar

Com Android SDK e JDK 17: `gradle assembleDebug --no-daemon` dentro de `anjun-inspector`. O GitHub Actions faz esse processo automaticamente em alterações na pasta ou no workflow.
