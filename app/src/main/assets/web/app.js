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
    const conn = document.getElementById("conn");

    let ws = null;
    let lastState = null;

    // ---------- Landing selection ----------
    const selection = { team: null, role: null };

    function wireLanding() {
        landing.classList.remove("hidden");
        document.querySelectorAll(".opt").forEach((btn) => {
            btn.addEventListener("click", () => {
                const g = btn.dataset.group;
                selection[g] = btn.dataset.value;
                document
                    .querySelectorAll(`.opt[data-group="${g}"]`)
                    .forEach((b) => b.classList.remove("selected"));
                btn.classList.add("selected");
                document.getElementById("enter").disabled = !(selection.team && selection.role);
            });
        });
        document.getElementById("enter").addEventListener("click", () => {
            team = selection.team;
            role = selection.role;
            startGame();
        });
    }

    // ---------- Game ----------
    function startGame() {
        landing.classList.add("hidden");
        game.classList.remove("hidden");
        if (role === "HOST") {
            hostControls.classList.remove("hidden");
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
        conn.textContent = text;
        conn.classList.toggle("error", !!isError);
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
            banner.textContent = "🏆 Time Vermelho venceu!";
            banner.classList.add("win-red");
        } else if (state.status === "BLUE_WINS") {
            banner.textContent = "🏆 Time Azul venceu!";
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
                el.dataset.index = i;
                el.addEventListener("click", () => {
                    const idx = Number(el.dataset.index);
                    const card = lastState && lastState.cards[idx];
                    if (lastState && lastState.canReveal && lastState.status === "PLAYING" && card && !card.revealed) {
                        send({ action: "reveal", index: idx });
                    }
                });
                board.appendChild(el);
            });
        }

        state.cards.forEach((card, i) => {
            const el = board.children[i];
            el.textContent = card.word;
            let cls = "card";
            if (card.color) cls += " " + card.color.toLowerCase();
            if (card.revealed) cls += " revealed";
            if (canTap && !card.revealed) cls += " tappable";
            el.className = cls;
        });
    }

    // ---------- Boot ----------
    if (role === "HOST" || role === "SPYMASTER" || role === "AGENT") {
        // Role given via URL (e.g. host WebView): skip landing.
        if (!team) team = "RED";
        startGame();
    } else {
        wireLanding();
    }
})();
