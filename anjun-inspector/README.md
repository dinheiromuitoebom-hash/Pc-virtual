# Anjun Auto Inspector — edição manual 0.2.0

Utilitário independente para documentar um fluxo de telas. Esta edição usa somente capturas que você escolhe no seletor de arquivos do Android. Os dados ficam no aparelho e a exportação depende de você selecionar um destino.

## O que funciona

- Escolher uma captura de tela por vez.
- Associar um título e observações à captura.
- Guardar até 40 telas localmente.
- Excluir um registro ou apagar todos após confirmação.
- Exportar um ZIP com as imagens e `registros.json` para análise manual.

O aplicativo não interpreta imagens com IA, não navega pela Anjun, não fica por cima de outros apps e não realiza baixas. Nenhuma integração Roteia está incluída.

## Por que a edição mudou

O usuário relatou o aviso “App bloqueado para proteger seu dispositivo” durante a instalação da versão 0.1.1. A combinação de instalação por navegador e serviço de acessibilidade é compatível com uma proteção documentada pelo Google. O diagnóstico não é uma classificação oficial do APK.

Na edição manual o serviço, sua configuração e o código de acessibilidade foram removidos do APK. A identidade do pacote continua `com.anjunauto.inspector`. Não há permissões de internet, SMS, notificações, sobreposição ou leitura ampla de arquivos. Os seletores do Android concedem acesso somente aos arquivos que você escolhe.

Esta é uma alternativa com capacidades reduzidas. Não representa a retomada da leitura automática e não garante aprovação do Google Play Protect. A versão anterior permanece no histórico do Git.

## Usar

1. Instale o APK da release manual mantendo o Play Protect ativo.
2. Abra o aplicativo e escolha uma captura, com dados de clientes ocultos.
3. Preencha o título e as observações e salve a tela.
4. Repita nas telas que desejar documentar.
5. Toque em exportar e escolha onde salvar o ZIP.

As imagens escolhidas são convertidas para PNG reduzido para até 1600 pixels no maior lado, sem preservar metadados EXIF. Isso não oculta visualmente dados pessoais: remova-os da captura antes de importar. As importações aceitam até 12 MiB por imagem. A exportação pode conter dados que você escreveu ou imagens que selecionou; revise antes de compartilhá-la.

## Validação

O workflow compila o APK, valida a assinatura, calcula o SHA-256 e executa uma checagem sobre o manifesto e o DEX compilados. A checagem exige ausência de permissões, serviços e APIs de captura automática. Não é uma verificação do Google Play Protect. A instalação no dispositivo do usuário ainda depende de validação.

## Fontes oficiais

- [Orientações sobre o aviso do Play Protect](https://developers.google.com/android/play-protect/warning-dev-guidance?hl=pt-br)
- [Seletores de documentos do Android](https://developer.android.com/training/data-storage/shared/documents-files)
- [Revisão de classificação pelo Google](https://support.google.com/googleplay/android-developer/contact/protectappeals?hl=en)

Para compilar com JDK 17 e Android SDK 35, execute `gradle assembleDebug --no-daemon` nesta pasta. O APK experimental usa assinatura de debug. O APK de cada release pode ter uma chave de debug diferente; não é um canal de atualização de produção.
