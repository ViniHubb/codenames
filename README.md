# Resenha — coleção de jogos de mesa (Android host + clientes via navegador)

Coleção de party games para jogar presencialmente. Um celular **Android** roda o app e sobe um
**servidor local** na rede Wi-Fi; os demais jogadores entram pelo **navegador**, sem instalar
nada. **Só o celular do mestre precisa do app.**

Jogos disponíveis:

| Jogo | O que é |
|---|---|
| **Codenames** | O Espião Mestre dá uma dica de uma palavra só e os agentes descobrem quais palavras são do time — sem esbarrar no assassino. |
| **Sintonia** | O mestre vê um alvo escondido entre dois conceitos opostos e dá uma dica. O time chuta a posição e o time adversário aposta de que lado o alvo está. |

O app abre num **lobby**: escolha o jogo e todos os navegadores já conectados **migram sozinhos**
para ele — ninguém precisa reescanear o QR.

## Como funciona (arquitetura)

- **App host (Android, Kotlin + Jetpack Compose):** shell fino com lobby, tela de conexão
  (IP + QR code) e a **mesa do mestre** em um WebView em tela cheia, **paisagem**.
- **Servidor embarcado (Ktor / engine CIO):** serve os web clients e expõe um WebSocket em
  `/ws`. Roda num **Foreground Service** para não ser morto com a tela bloqueada.
- **Web client (HTML/CSS/JS puro):** servido de `app/src/main/assets/web/`, uma pasta por jogo
  mais uma `shared/` com o que os dois usam (conexão, reconexão, tela cheia, keep-awake).
- **Fonte única da verdade:** o motor do jogo ativo, no host. O servidor envia **snapshots
  filtrados por função** — quem não pode ver um segredo simplesmente não o recebe (anti-trapaça).

```
Celular MESTRE (app)                       Outros aparelhos (navegador)
┌───────────────────────────┐             ┌─────────────────────────┐
│ Compose: lobby + QR/IP    │    Wi-Fi    │ http://IP:8080/         │
│ WebView /<jogo>/?role=host│ ──────────> │  → /codenames/ ou       │
│ Ktor :8080  /  +  /ws     │  WebSocket  │    /sintonia/           │
│ Motor do jogo (estado)    │ <────────── │                         │
└───────────────────────────┘             └─────────────────────────┘
```

## Codenames

- **Host** (no app): tabuleiro em paisagem com o mapa completo; **toque longo para revelar** as
  cartas (anti-misclick). Barra superior com **← voltar**, turno, contadores e os botões
  **⏭ passar turno** e **↻ nova partida**.
- **Espião Mestre** (navegador): vê o mapa completo, somente leitura.
- **Agente** (navegador): vê só as palavras; cartas reveladas ganham a cor.

O time escolhido é apenas cosmético — a tela depende da **função**, não do time. Na entrada, só
dá para escolher a função **depois** do time. Modos de palavras: Clássico / Adjetivos / Verbos /
Tudo; trocar o modo re-sorteia o tabuleiro.

## Sintonia

Só o mestre interage; todo mundo assiste pelo navegador (espectro, dica, ponteiro ao vivo e
placar). O ponteiro anda de 0 a 100 entre os dois conceitos da carta.

Uma rodada, em quatro tempos — o mestre conduz todos eles:

1. **Dica** — o app sorteia uma carta e esconde um alvo no espectro. **Só o mestre vê o alvo.**
   Ele escreve a dica (ou fala em voz alta) e envia.
2. **Palpite** — o time da vez debate e o mestre **arrasta o ponteiro** até onde eles decidirem.
   O ponteiro se move ao vivo na tela de todo mundo. Ele trava o palpite.
3. **Aposta** — o time adversário diz de que **lado** do ponteiro acha que o alvo está, e o
   mestre registra.
4. **Revelação** — o alvo aparece para todos e os pontos entram no placar.

Pontuação (como no jogo físico): o time que chutou ganha **4, 3 ou 2 pontos** conforme a faixa
em que o ponteiro caiu, e o adversário ganha **1 ponto** se acertou o lado — as duas pontuações
são independentes. A única exceção é o ponteiro parar **exatamente** em cima do alvo: aí não
existe lado para apostar e a aposta não pontua. Os times se alternam a cada rodada e **vence
quem chegar a 10 pontos** primeiro (empate em 10 não encerra: a partida continua).

O mestre é fixo: quem está com o celular. Ele dá dicas para os dois times, alternadamente.
Modos de cartas: Clássico / Picante / Tudo.

## Instalar sem PC (via Releases)
A cada push na `main`, o GitHub Actions roda os testes, compila um **APK de debug** e o publica
na release **latest**:
1. Abra **[Releases](https://github.com/ViniHubb/resenha/releases)** e baixe
   `resenha-debug.apk` (link direto:
   `https://github.com/ViniHubb/resenha/releases/download/latest/resenha-debug.apk`).
2. No Android, permita **instalar de fontes desconhecidas** e instale.
3. **Só o celular do mestre precisa do app**; os jogadores entram pelo navegador.

> É um APK de **debug** (uso pessoal). Dá para disparar um build manualmente em
> **Actions → Build APK → Run workflow**.
>
> Vindo da versão antiga "Codenames"? O `applicationId` mudou (`com.codenames.host` →
> `com.resenha.host`), então **desinstale o app antigo antes** — o Android trata os dois como
> apps diferentes.

## Pré-requisitos
- **Android Studio** recente (Ladybug/2024.2 ou mais novo). O Android Studio **já inclui o JDK**
  necessário (JBR 17+), então não é preciso instalar Java à parte.
- **Android SDK Platform 34** (o projeto usa `compileSdk`/`targetSdk` 34).
- Um celular **Android (API 26+ / Android 8.0+)** para ser o host.

> **Versões fixadas:** AGP 8.6.1 / Gradle 8.9 / Kotlin 2.0.20. Num Android Studio mais novo pode
> aparecer sugestão de "upgrade do Android Gradle Plugin" — pode **ignorar/Skip**.

## Build & execução
1. Abra a pasta do projeto no Android Studio e aguarde o **Gradle sync**.
2. Conecte o celular por USB (com **Depuração USB** ligada) ou use um emulador.
3. **Run ▶** o módulo `app` (ou `./gradlew installDebug`).
4. Escolha o jogo no lobby. A tela seguinte mostra `http://<ip-do-celular>:8080` + QR code
   (toque no IP para copiar).
5. Nos outros aparelhos (mesma rede Wi-Fi), abra essa URL no navegador.
6. No host, toque em **Abrir mesa**. O **← voltar** (ou o gesto do Android) volta.

### Dica de rede (importante)
Muitas redes Wi-Fi têm **isolamento de clientes (AP isolation)**, que impede um aparelho de falar
com o outro. Se os clientes não conectarem, use o **hotspot do próprio celular host**: ative o
roteador Wi-Fi dele, conecte os demais aparelhos e use o IP exibido no app.

## Testes
A lógica dos dois jogos é Kotlin puro e tem testes unitários (também rodados na CI):
```
./gradlew testDebugUnitTest
```

### Verificação manual ponta a ponta
**Codenames**
- Revelar carta do time atual → vira, **turno continua**; neutra/adversária → **turno passa**.
- Revelar o **assassino** → fim de jogo, time atual perde (refletido em todos).
- **Anti-trapaça:** no DevTools de um cliente Agente, o payload do WebSocket traz
  `"color": null` para cartas não reveladas.

**Sintonia**
- No celular do mestre o alvo aparece atrás do espectro; no navegador **não**.
- **Anti-trapaça:** no DevTools de um espectador, o payload traz `"target": null` até a revelação.
- Arrastar o ponteiro no host o move ao vivo em todos os navegadores.
- Enviar dica → palpite → maior/menor → revelar: o alvo aparece para todos e o placar sobe
  conforme a faixa. Ao chegar a 10, aparece a tela de vitória.

**Coleção**
- Com clientes conectados, voltar ao lobby e trocar de jogo: os navegadores migram sozinhos.

## Estrutura
```
app/src/main/
  java/com/resenha/host/
    MainActivity.kt              Permissões + start do service + tema
    ui/AppRoot.kt                Navegação: lobby → conexão → mesa
    ui/LobbyScreen.kt            Escolha do jogo
    ui/Games.kt                  Catálogo: título, descrição e modos de cada jogo
    ui/StartScreen.kt            IP copiável, QR, contador, seletor de modo
    ui/HostBoardScreen.kt        WebView da mesa (paisagem) + ponte JS p/ voltar
    server/
      GameServer.kt              Ktor: estático + /ws + broadcast + jogo ativo
      HostedGame.kt              Contrato de um jogo hospedável
      Wire.kt                    Role, ActionDto e config do JSON
      Teams.kt                   Team / GameStatus (compartilhados)
      ServerService.kt           Foreground Service
      ServerState.kt             Ponte service ↔ UI (StateFlows)
      NetworkUtils.kt            IP local + bitmap do QR
      codenames/                 GameState, Snapshots, GameMode, WordBank, CodenamesGame
      sintonia/                  SintoniaEngine, SintoniaSnapshots, SintoniaMode,
                                 SpectrumBank, SintoniaGame
  assets/
    web/index.html               Ponto de entrada: redireciona para o jogo ativo
    web/shared/                  common.js, base.css, nosleep.min.js
    web/codenames/               index.html, app.js, style.css
    web/sintonia/                index.html, app.js, style.css
    words_*.json                 Bancos de palavras do Codenames (por tema)
    spectrums_*.json             Cartas de espectro do Sintonia (por tema)
```

## Próximos passos
Ideias futuras: timer por rodada, efeitos sonoros/vibração, listas customizadas pelo usuário,
nomes de jogadores, múltiplos idiomas, persistir/restaurar sessão e novos jogos na coleção.
