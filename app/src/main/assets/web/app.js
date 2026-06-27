"use strict";

(function () {
    const params = new URLSearchParams(location.search);
    let role = (params.get("role") || "").toUpperCase(); // HOST | SPYMASTER | AGENT
    let team = (params.get("team") || "").toUpperCase(); // RED | BLUE (cosmetic)

    const landing = document.getElementById("landing");
    const game = document.getElementById("game");
    const board = document.getElementById("board");
    const banner = document.getElementById("banner");
    const redCount = document.getElementById("redCount");
    const blueCount = document.getElementById("blueCount");
    const hostControls = document.getElementById("hostControls");
    const fsBtn = document.getElementById("fsBtn");
    const backBtn = document.getElementById("backBtn");

    let ws = null;
    let lastState = null;

    // ---------- Landing selection ----------
    const selection = { team: null, role: null };

    function wireLanding() {
        landing.classList.remove("hidden");
        const teamBtns = document.querySelectorAll('.opt[data-group="team"]');
        const roleBtns = document.querySelectorAll('.opt[data-group="role"]');
        const enter = document.getElementById("enter");

        function teamColor() {
            if (selection.team === "RED") return "var(--red)";
            if (selection.team === "BLUE") return "var(--blue)";
            return "";
        }
        // The chosen role button is painted with the selected team's color.
        function paintSelectedRole() {
            roleBtns.forEach((b) => {
                b.style.background = b.classList.contains("selected") ? teamColor() : "";
            });
        }
        function refreshEnter() {
            enter.disabled = !(selection.team && selection.role);
        }

        teamBtns.forEach((btn) => {
            btn.addEventListener("click", () => {
                selection.team = btn.dataset.value;
                teamBtns.forEach((b) => b.classList.remove("selected"));
                btn.classList.add("selected");
                roleBtns.forEach((b) => { b.disabled = false; }); // unlock roles after a team
                paintSelectedRole();
                refreshEnter();
            });
        });

        roleBtns.forEach((btn) => {
            btn.addEventListener("click", () => {
                if (!selection.team) return; // can't pick a role before a team
                selection.role = btn.dataset.value;
                roleBtns.forEach((b) => b.classList.remove("selected"));
                btn.classList.add("selected");
                paintSelectedRole();
                refreshEnter();
            });
        });

        enter.addEventListener("click", () => {
            team = selection.team;
            role = selection.role;
            requestFullscreen(); // this click is a user gesture, so the browser allows it
            enableKeepAwake(); // keep the player's screen on (needs this gesture)
            startGame();
        });
    }

    // ---------- Fullscreen ----------
    function isFullscreen() {
        return !!(document.fullscreenElement || document.webkitFullscreenElement);
    }

    function requestFullscreen() {
        const el = document.documentElement;
        const req = el.requestFullscreen || el.webkitRequestFullscreen;
        if (req) {
            try {
                Promise.resolve(req.call(el)).catch(() => {});
            } catch (e) {
                /* not supported (e.g. iOS Safari) */
            }
        }
    }

    function exitFullscreen() {
        const exit = document.exitFullscreen || document.webkitExitFullscreen;
        if (exit) {
            try {
                Promise.resolve(exit.call(document)).catch(() => {});
            } catch (e) {
                /* ignore */
            }
        }
    }

    function toggleFullscreen() {
        if (isFullscreen()) exitFullscreen();
        else requestFullscreen();
    }

    function updateFsIcon() {
        fsBtn.textContent = isFullscreen() ? "✕" : "⛶";
        fsBtn.title = isFullscreen() ? "Sair da tela cheia" : "Tela cheia";
    }

    // ---------- Keep awake (players) ----------
    // Over plain http the Wake Lock API isn't available, so NoSleep.js falls back to a hidden
    // looping video. It must be enabled from a user gesture (the "Entrar" tap).
    let noSleep = null;
    function enableKeepAwake() {
        try {
            if (!noSleep && typeof NoSleep !== "undefined") noSleep = new NoSleep();
            if (noSleep) noSleep.enable();
        } catch (e) {
            /* not supported in this browser */
        }
    }
    document.addEventListener("visibilitychange", () => {
        if (!document.hidden && noSleep) {
            try {
                noSleep.enable();
            } catch (e) {
                /* ignore */
            }
        }
    });

    // ---------- Game ----------
    function startGame() {
        landing.classList.add("hidden");
        game.classList.remove("hidden");
        // HOST/SPYMASTER see the full map; AGENT only sees revealed cards. This drives the
        // contrast scheme (see style.css): map = vibrant until marked, agent = vibrant once marked.
        document.body.classList.add(role === "AGENT" ? "agent-view" : "map-view");
        fitViewport();
        if (role === "HOST") {
            hostControls.classList.remove("hidden");
            backBtn.classList.remove("hidden");
            backBtn.addEventListener("click", () => {
                if (window.AndroidHost && window.AndroidHost.back) window.AndroidHost.back();
            });
            document.getElementById("passTurn").addEventListener("click", () =>
                send({ action: "passTurn" })
            );
            document.getElementById("newGame").addEventListener("click", () => {
                if (confirm("Iniciar uma nova partida?")) send({ action: "newGame" });
            });
        }
        connect();
    }

    function connect() {
        const proto = location.protocol === "https:" ? "wss" : "ws";
        const url = `${proto}://${location.host}/ws?role=${encodeURIComponent(role)}&team=${encodeURIComponent(team)}`;
        ws = new WebSocket(url);
        ws.onopen = () => setConn("");
        ws.onmessage = (e) => {
            try {
                const msg = JSON.parse(e.data);
                if (msg.type === "state") render(msg);
            } catch (err) {
                /* ignore malformed */
            }
        };
        ws.onclose = () => {
            setConn("Reconectando…", true);
            setTimeout(connect, 1500);
        };
        ws.onerror = () => ws.close();
    }

    function send(obj) {
        if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
    }

    function setConn(text, isError) {
        // Connection status is shown in the turn chip; a fresh state message overwrites it.
        if (text) {
            banner.textContent = text;
            banner.className = "banner" + (isError ? " conn-error" : "");
        }
    }

    // Some Android WebViews resolve CSS viewport/percentage heights unreliably, which leaves the
    // grid stuck at a tiny height with empty space below. Pin the real height measured in JS so
    // the board's flex:1 has a concrete height to fill. Recompute on resize/rotation.
    function fitViewport() {
        const h = window.innerHeight || document.documentElement.clientHeight;
        if (h) game.style.height = h + "px";
    }

    // ---------- Rendering ----------
    function render(state) {
        lastState = state;
        renderBanner(state);
        redCount.textContent = state.redRemaining;
        blueCount.textContent = state.blueRemaining;
        renderBoard(state);
    }

    function renderBanner(state) {
        banner.className = "banner";
        if (state.status === "RED_WINS") {
            banner.textContent = "🏆 Vermelho venceu!";
            banner.classList.add("win-red");
        } else if (state.status === "BLUE_WINS") {
            banner.textContent = "🏆 Azul venceu!";
            banner.classList.add("win-blue");
        } else {
            const t = state.currentTurn === "RED" ? "Vermelho" : "Azul";
            banner.textContent = `Vez do time ${t}`;
            banner.classList.add(state.currentTurn === "RED" ? "turn-red" : "turn-blue");
        }
    }

    function renderBoard(state) {
        const playing = state.status === "PLAYING";
        const canTap = state.canReveal && playing;

        // Rebuild only if card count changed; otherwise update in place to avoid flicker.
        if (board.childElementCount !== state.cards.length) {
            board.innerHTML = "";
            state.cards.forEach((_, i) => {
                const el = document.createElement("div");
                el.className = "card";
                el.dataset.index = i;
                el.innerHTML = '<span class="fill"></span><span class="word"></span>';
                // Revealing requires a long press (anti-misclick) — see startHold/cancelHold.
                el.addEventListener("pointerdown", (e) => onPress(e, el));
                el.addEventListener("pointerup", () => cancelHold(el));
                el.addEventListener("pointerleave", () => cancelHold(el));
                el.addEventListener("pointercancel", () => cancelHold(el));
                el.addEventListener("contextmenu", (e) => e.preventDefault());
                board.appendChild(el);
            });
        }

        state.cards.forEach((card, i) => {
            const el = board.children[i];
            el.querySelector(".word").textContent = card.word;
            let cls = "card";
            if (card.color) cls += " " + card.color.toLowerCase();
            if (card.revealed) cls += " revealed";
            if (canTap && !card.revealed) cls += " tappable";
            el.className = cls;
        });
    }

    // ---------- Long-press to reveal (host, anti-misclick) ----------
    // Duration comes from --hold-ms in style.css so the JS timer and the fill animation stay in sync.
    const HOLD_MS =
        parseInt(getComputedStyle(document.documentElement).getPropertyValue("--hold-ms")) || 2500;

    function onPress(e, el) {
        const idx = Number(el.dataset.index);
        const card = lastState && lastState.cards[idx];
        const eligible =
            lastState && lastState.canReveal && lastState.status === "PLAYING" && card && !card.revealed;
        if (!eligible) return;
        e.preventDefault();
        startHold(el, idx);
    }

    function startHold(el, idx) {
        cancelHold(el);
        el.classList.add("holding"); // triggers the CSS fill transition over --hold-ms
        el._holdTimer = setTimeout(() => {
            el.classList.remove("holding");
            el._holdTimer = null;
            send({ action: "reveal", index: idx });
        }, HOLD_MS);
    }

    function cancelHold(el) {
        if (el._holdTimer) {
            clearTimeout(el._holdTimer);
            el._holdTimer = null;
        }
        el.classList.remove("holding"); // resets the fill quickly
    }

    // ---------- Boot ----------
    fitViewport();
    window.addEventListener("resize", fitViewport);
    window.addEventListener("orientationchange", () => setTimeout(fitViewport, 150));

    // Fullscreen toggle is for browser players only; the host runs inside the app's WebView.
    if (role === "HOST") {
        fsBtn.classList.add("hidden");
    } else {
        fsBtn.addEventListener("click", toggleFullscreen);
        document.addEventListener("fullscreenchange", updateFsIcon);
        document.addEventListener("webkitfullscreenchange", updateFsIcon);
    }

    if (role === "HOST" || role === "SPYMASTER" || role === "AGENT") {
        // Role given via URL (e.g. host WebView): skip landing.
        if (!team) team = "RED";
        startGame();
    } else {
        wireLanding();
    }
})();
