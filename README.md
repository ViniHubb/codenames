# Codenames — companion app (Android host + clientes via navegador)

Adaptação mobile do jogo de tabuleiro **Codenames**. Um celular **Android** roda o app e sobe
um **servidor local** na rede Wi-Fi; os demais jogadores entram pelo **navegador** (sem
instalar nada), escolhem time e função e veem a tela apropriada. As cartas são reveladas
**no celular host** e a mudança é propagada para todos em tempo real.

## Como funciona (arquitetura)

- **App host (Android, Kotlin + Jetpack Compose):** shell fino que mostra o endereço de
  conexão + QR code e abre o tabuleiro do host num WebView.
- **Servidor embarcado (Ktor / engine CIO):** serve o web client e expõe um WebSocket em
  `/ws`. Roda num **Foreground Service** para não ser morto com a tela bloqueada.
- **Web client (HTML/CSS/JS puro):** servido a partir de `app/src/main/assets/web/`.
- **Fonte única da verdade:** o `GameEngine` no host. O servidor envia **snapshots filtrados
  por função** — Agentes nunca recebem a cor de cartas não reveladas (anti-trapaça).

```
Celular HOST (app)                         Outros aparelhos (navegador)
┌───────────────────────────┐             ┌───────────────────────┐
│ Compose: QR + IP:porta     │   Wi-Fi     │ http://IP:8080/        │
│ WebView  /?role=host       │ ─────────▶  │  • Espião Mestre (mapa)│
│ Ktor :8080  /  +  /ws  ◀───┼─ WebSocket ─┤  • Agente (palavras)   │
│ GameEngine (estado)        │             │                       │
└───────────────────────────┘             └───────────────────────┘
```

### Funções e telas
- **Host** (no próprio app): vê o mapa completo e **toca para revelar** as cartas.
- **Espião Mestre** (navegador): vê o mapa completo, somente leitura.
- **Agente** (navegador): vê só as palavras; cartas reveladas ganham a cor.

O time escolhido é apenas cosmético — a tela depende da função, não do time.

## Pré-requisitos
- **Android Studio** (Ladybug/2024.2 ou mais novo) com JDK 17 e o Android SDK 34.
- Um celular Android (API 26+ / Android 8.0+) para ser o host.

> Este repositório **não inclui o binário `gradle-wrapper.jar`**. Ao **abrir o projeto no
> Android Studio**, ele provisiona o Gradle automaticamente (via `gradle/wrapper/gradle-wrapper.properties`).
> Se preferir a linha de comando e tiver o Gradle instalado, rode `gradle wrapper` uma vez
> para gerar o wrapper e o `gradlew`/`gradlew.bat`.

## Build & execução
1. Abra a pasta do projeto no Android Studio e aguarde o **Gradle sync**.
2. Conecte o celular host por USB (com **Depuração USB** ligada) ou use um emulador.
3. **Run ▶** o módulo `app`. (Ou, via CLI após gerar o wrapper: `./gradlew installDebug`.)
4. Ao abrir, o app mostra `http://<ip-do-celular>:8080` + QR code.
5. Nos outros aparelhos (mesma rede Wi-Fi), abra essa URL no navegador, escolha **time** e
   **função** e toque em **Entrar**.
6. No host, toque em **Abrir tabuleiro (Host)** para revelar cartas e usar
   **Passar turno** / **Nova partida**.

### Dica de rede (importante)
Muitas redes Wi-Fi públicas/domésticas têm **isolamento de clientes (AP isolation)**, que
impede um aparelho de falar com o outro. Se os clientes não conectarem, use o **hotspot do
próprio celular host**: ative o roteador Wi-Fi do host, conecte os demais aparelhos nele e
use o IP exibido no app.

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
    ui/StartScreen.kt            Tela inicial (URL, QR, contador, ações)
    ui/HostBoardScreen.kt        WebView do tabuleiro do host
    server/
      GameState.kt               GameEngine: modelo, setup, revelar, vitória
      Snapshots.kt               DTOs + filtro por função (anti-trapaça)
      WordBank.kt                Carrega o banco de palavras
      GameServer.kt              Ktor: estático + /ws + broadcast
      ServerService.kt           Foreground Service
      ServerState.kt             Ponte service ↔ UI (StateFlows)
      NetworkUtils.kt            IP local + bitmap do QR
  assets/
    web/ index.html, app.js, style.css
    words_ptbr.json              Banco de palavras PT-BR
```

## Escopo do v1 e próximos passos
Incluído: visões Mapa/Neutra, revelar no host com propagação, detecção de vitória/assassino,
contadores e indicador de turno, QR code, reconexão automática do cliente.

Fora do v1 (ideias futuras): timer por turno, efeitos sonoros/vibração, temas/listas
customizadas de palavras, nomes de jogadores, múltiplos idiomas, persistir/restaurar sessão.
