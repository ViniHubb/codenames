"use strict";

(function () {
    const R = window.Resenha;
    const GAME_ID = "codenames";

    const landing = document.getElementById("landing");
    const game = document.getElementById("game");
    const board = document.getElementById("board");
    const banner = document.getElementById("banner");
    const redCount = document.getElementById("redCount");
    const blueCount = document.getElementById("blueCount");
    const hostControls = document.getElementById("hostControls");
    const backBtn = document.getElementById("backBtn");

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
            R.setIdentity(selection.role, selection.team);
            R.requestFullscreen(); // this click is a user gesture, so the browser allows it
            R.enableKeepAwake(); // keep the player's screen on (needs this gesture)
            startGame();
        });
    }

    // ---------- Game ----------
    function startGame() {
        landing.classList.add("hidden");
        game.classList.remove("hidden");
        // HOST/SPYMASTER see the full map; AGENT only sees revealed cards. This drives the
        // contrast scheme (see style.css): map = vibrant until marked, agent = vibrant once marked.
        const map = R.role === "HOST" || R.role === "SPYMASTER";
        document.body.classList.add(map ? "map-view" : "agent-view");
        R.trackViewport(game);
        if (R.role === "HOST") {
            hostControls.classList.remove("hidden");
            backBtn.classList.remove("hidden");
            backBtn.addEventListener("click", () => R.goBack());
            document.getElementById("passTurn").addEventListener("click", () =>
                R.send({ action: "passTurn" })
            );
            document.getElementById("newGame").addEventListener("click", () => {
                if (confirm("Iniciar uma nova partida?")) R.send({ action: "newGame" });
            });
        }
        R.start(GAME_ID, { onState: render, onConn: setConn });
    }

    function setConn(text, isError) {
        // Connection status is shown in the turn chip; a fresh state message overwrites it.
        if (text) {
            banner.textContent = text;
            banner.className = "banner" + (isError ? " conn-error" : "");
        }
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
            R.send({ action: "reveal", index: idx });
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
    R.wireFullscreen(document.getElementById("fsBtn"));

    if (R.role === "HOST" || R.role === "SPYMASTER" || R.role === "AGENT") {
        // Role given via URL (e.g. host WebView): skip landing.
        if (!R.team) R.setIdentity(null, "RED");
        startGame();
    } else {
        wireLanding();
    }
})();
