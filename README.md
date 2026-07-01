# Codenames — companion app (Android host + clientes via navegador)

Adaptação mobile do jogo de tabuleiro **Codenames**. Um celular **Android** roda o app e sobe
um **servidor local** na rede Wi-Fi; os demais jogadores entram pelo **navegador** (sem
instalar nada), escolhem time e função e veem a tela apropriada. As cartas são reveladas
**no celular host** e a mudança é propagada para todos em tempo real.

## Como funciona (arquitetura)

- **App host (Android, Kotlin + Jetpack Compose):** shell fino com a tela inicial (IP + QR
  code) e o **tabuleiro do host** em um WebView que ocupa a tela inteira em **paisagem**.
- **Servidor embarcado (Ktor / engine CIO):** serve o web client e expõe um WebSocket em
  `/ws`. Roda num **Foreground Service** para não ser morto com a tela bloqueada.
- **Web client (HTML/CSS/JS puro):** servido a partir de `app/src/main/assets/web/`. É o mesmo
  código para o host (`/?role=host`, com toque-para-revelar) e para os jogadores.
- **Fonte única da verdade:** o `GameEngine` no host. O servidor envia **snapshots filtrados
  por função** — Agentes nunca recebem a cor de cartas não reveladas (anti-trapaça).

```
Celular HOST (app)                         Outros aparelhos (navegador)
┌───────────────────────────┐             ┌─────────────────────────┐
│ Compose: QR + IP:porta    │    Wi-Fi    │ http://IP:8080/         │
│ WebView  /?role=host      │ ──────────> │  • Espião Mestre (mapa) │
│ Ktor :8080  /  +  /ws     │  WebSocket  │  • Agente (palavras)    │
│ GameEngine (estado)       │ <────────── │                         │
└───────────────────────────┘             └─────────────────────────┘
```

### Funções e telas
- **Host** (no próprio app): tabuleiro em paisagem com o mapa completo; **toca para revelar** as
  cartas. A barra superior reúne, em uma única linha: **← voltar**, indicador de turno,
  contadores e os botões **⏭ passar turno** e **↻ nova partida**.
- **Espião Mestre** (navegador): vê o mapa completo, somente leitura.
- **Agente** (navegador): vê só as palavras; cartas reveladas ganham a cor.

Detalhes de UX:
- O time escolhido é apenas cosmético — a tela depende da **função**, não do time. Na entrada,
  só é possível escolher a função **depois** de escolher o time (a função selecionada fica com
  a cor do time).
- Jogadores no navegador têm um **botão de tela cheia** (entra em tela cheia automaticamente ao
  tocar em "Entrar"; no iOS use "Adicionar à Tela de Início").
- Na tela inicial do host, **toque no IP para copiá-lo**.
- A tela inicial do host tem um seletor de **Modo** (Clássico/Adjetivos/Verbos/Tudo); trocar o
  modo re-sorteia o tabuleiro com o novo tema de palavras.
- Cores do mapa: vermelho, azul, **cinza** (neutra) e preto (assassino).

## Instalar sem PC (via Releases)
A cada push na `main`, o GitHub Actions compila um **APK de debug** e o publica na release
**latest** — dá pra instalar em qualquer celular Android só com internet:
1. Abra **[Releases](https://github.com/ViniHubb/codenames/releases)** e baixe
   `codenames-host-debug.apk` (link direto:
   `https://github.com/ViniHubb/codenames/releases/download/latest/codenames-host-debug.apk`).
2. No Android, permita **instalar de fontes desconhecidas** (pedido na hora pelo navegador) e instale.
3. **Só o celular Servidor (host) precisa do app**; os jogadores entram pelo navegador.

> É um APK de **debug** (uso pessoal). Você também pode disparar um build manualmente em
> **Actions → Build APK → Run workflow**.

## Pré-requisitos
- **Android Studio** recente (Ladybug/2024.2 ou mais novo — também testado na linha 2026.x). O
  Android Studio **já inclui o JDK** necessário (JBR 17+), então não é preciso instalar Java à
  parte.
- **Android SDK Platform 34** (o projeto usa `compileSdk`/`targetSdk` 34). Se faltar, o Android
  Studio oferece instalar no primeiro sync ("Install missing SDK package(s)").
- Um celular **Android (API 26+ / Android 8.0+)** para ser o host.

> **Versões fixadas:** o projeto usa AGP 8.6.1 / Gradle 8.9 / Kotlin 2.0.20. Em um Android
> Studio bem mais novo pode aparecer uma sugestão de "upgrade do Android Gradle Plugin" — pode
> **ignorar/Skip**.
>
> **Wrapper:** este repositório **não inclui o binário `gradle-wrapper.jar`**. Ao abrir o
> projeto no Android Studio, ele provisiona o Gradle automaticamente (via
> `gradle/wrapper/gradle-wrapper.properties`). Para usar a linha de comando, rode
> `gradle wrapper` uma vez para gerar o `gradlew`/`gradlew.bat`.

## Build & execução
1. Abra a pasta do projeto no Android Studio e aguarde o **Gradle sync** (instale a API 34 se
   ele pedir).
2. Conecte o celular host por USB (com **Depuração USB** ligada) ou use um emulador.
3. **Run ▶** o módulo `app`. (Ou, via CLI após gerar o wrapper: `./gradlew installDebug`.)
4. Ao abrir, o app mostra `http://<ip-do-celular>:8080` + QR code (toque no IP para copiar).
5. Nos outros aparelhos (mesma rede Wi-Fi), abra essa URL no navegador, escolha **time** e
   **função** e toque em **Entrar**.
6. No host, toque em **Abrir tabuleiro** para revelar cartas e usar **⏭ passar turno** /
   **↻ nova partida**. O **← voltar** (ou o gesto/voltar do Android) retorna à tela inicial.

### Dica de rede (importante)
Muitas redes Wi-Fi públicas/domésticas têm **isolamento de clientes (AP isolation)**, que
impede um aparelho de falar com o outro. Se os clientes não conectarem, use o **hotspot do
próprio celular host**: ative o roteador Wi-Fi do host, conecte os demais aparelhos nele e
use o IP exibido no app.

## Gerar um APK
Quando você roda via **Run ▶**, o Android Studio já instala um **APK de debug** no aparelho —
ele continua funcionando sem o cabo/IDE. **Só o celular host precisa do app**; os jogadores
usam o navegador.
- **Debug (uso pessoal):** copie `app/build/outputs/apk/debug/app-debug.apk` e instale em outro
  aparelho (habilite "instalar de fontes desconhecidas").
- **Release (distribuição):** Android Studio → **Build → Generate Signed Bundle / APK → APK**,
  criando uma keystore uma vez (gera um APK otimizado e assinado com a sua chave).

## Testes
A lógica do jogo é Kotlin puro e tem testes unitários:
```
./gradlew testDebugUnitTest
```
(`app/src/test/java/com/codenames/host/GameEngineTest.kt`)

### Verificação manual ponta a ponta
- Host revela carta do time atual → vira, **turno continua**.
- Host revela neutra/adversária → vira, **turno passa**.
- Host revela o **assassino** → fim de jogo, time atual perde (refletido em todos).
- Revelar todas as cartas de um time → vitória.
- **Anti-trapaça:** no DevTools de um cliente Agente, o payload do WebSocket deve trazer
  `"color": null` para cartas não reveladas.

## Estrutura
```
app/src/main/
  java/com/codenames/host/
    MainActivity.kt              UI raiz + permissão + start do service
    ui/StartScreen.kt            Tela inicial (IP copiável, QR, contador, abrir tabuleiro)
    ui/HostBoardScreen.kt        WebView do tabuleiro (tela cheia/paisagem) + ponte JS p/ voltar
    server/
      GameState.kt               GameEngine: modelo, setup, revelar, vitória
      Snapshots.kt               DTOs + filtro por função (anti-trapaça)
      GameMode.kt                Enum dos modos (Clássico/Adjetivos/Verbos/Tudo)
      WordBank.kt                Carrega os bancos por tema + mistura do modo "Tudo"
      GameServer.kt              Ktor: estático + /ws + broadcast
      ServerService.kt           Foreground Service
      ServerState.kt             Ponte service ↔ UI (StateFlows)
      NetworkUtils.kt            IP local + bitmap do QR
  assets/
    web/ index.html, app.js, style.css, nosleep.min.js
    words_substantivos.json      Banco por tema (substantivos — modo Clássico)
    words_adjetivos.json         Banco por tema (adjetivos)
    words_verbos.json            Banco por tema (verbos)
```

## Escopo do v1 e próximos passos
Incluído: visões Mapa/Neutra, revelar no host (toque longo, anti-misclick) com propagação em
tempo real, detecção de vitória/assassino, contadores e indicador de turno, **modos de palavras
por tema** (Clássico/Adjetivos/Verbos/Tudo) selecionados no host, QR code + IP copiável, tabuleiro
do host em tela cheia/paisagem, controles em linha única com ícones, tela cheia no navegador dos
jogadores, tela sempre acordada (host + jogadores) e reconexão automática do cliente.

Fora do v1 (ideias futuras): timer por turno, efeitos sonoros/vibração, listas de palavras
customizadas pelo usuário, nomes de jogadores, múltiplos idiomas, persistir/restaurar sessão.
