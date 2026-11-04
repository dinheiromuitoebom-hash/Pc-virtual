# Registro de diagnóstico — instalação bloqueada

## Evidência

- O usuário informou “App bloqueado para proteger seu dispositivo” ao tentar instalar, antes da ativação de permissões.
- A versão 0.1.1 distribuída pelo GitHub declara `BIND_ACCESSIBILITY_SERVICE` e `canRetrieveWindowContent=true`.
- O código dessa versão não solicita internet, SMS, notificações ou sobreposição e não executa cliques. Mesmo assim, a capacidade de leitura de janelas era declarada ao Android.
- A compilação e a assinatura da versão 0.1.1 foram validadas. Isso não equivale à aprovação do Play Protect.

## Hipótese sustentada pelas fontes

O Google documenta esse aviso para instalações vindas de navegador/mensageiro/gerenciador de arquivos que declaram acesso sensível, incluindo acessibilidade. A combinação corresponde ao APK anterior. Somente o título informado, sem uma decisão formal do Google, não confirma a classificação exata.

Fonte: https://developers.google.com/android/play-protect/warning-dev-guidance?hl=pt-br

## Alteração real de capacidade

A edição 0.2.0-manual remove serviço, configuração XML e implementação da acessibilidade. Toda importação e exportação é iniciada pelo usuário por meio dos seletores do Android. O pacote mantém a mesma identidade e não existe mecanismo que recupere a capacidade removida após instalar.

O arquivo `scripts/verify_manual_apk.py` inspeciona o APK compilado para verificar a remoção. Não foi feito um teste do Play Protect em um aparelho físico e não se promete instalação garantida.

## Caso a versão automática seja retomada

Antes de qualquer publicação, revisar finalidade de acesso, consentimento, visibilidade de sessão, pausa, limites de pacote e proteção de dados. Não declarar `isAccessibilityTool=true` para esta finalidade, que não é uma ferramenta voltada a pessoas com deficiência. Se houver motivo para considerar o bloqueio incorreto após a revisão, o Google oferece um canal oficial de contestação.

Canal: https://support.google.com/googleplay/android-developer/contact/protectappeals?hl=en

Nenhuma contestação foi enviada nesta alteração. O formulário exige sessão e declaração pelo responsável. Não usar mudança de nome, identidade de pacote, ocultação de permissões ou desativação do Play Protect como solução.
