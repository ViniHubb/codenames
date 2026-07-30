"use strict";

(function () {
    const R = window.Resenha;
    const GAME_ID = "sintonia";
    const POINTER_THROTTLE_MS = 50; // cap the drag stream so the socket isn't flooded
    const NUDGE_STEP = 1; // dial units per arrow tap
    const NUDGE_DELAY_MS = 350; // hold this long before the arrow starts repeating
    const NUDGE_REPEAT_MS = 70;

    const game = document.getElementById("game");
    const banner = document.getElementById("banner");
    const scoreRed = document.getElementById("scoreRed");
    const scoreBlue = document.getElementById("scoreBlue");
    const roundLabel = document.getElementById("roundLabel");
    const hostControls = document.getElementById("hostControls");
    const backBtn = document.getElementById("backBtn");

    const leftLabel = document.getElementById("leftLabel");
    const rightLabel = document.getElementById("rightLabel");
    const dial = document.getElementById("dial");
    const target = document.getElementById("target");
    const needle = document.getElementById("needle");
    const clue = document.getElementById("clue");
    const result = document.getElementById("result");

    const controls = document.getElementById("controls");
    const phases = {
        CLUE: document.getElementById("cluePhase"),
        GUESS: document.getElementById("guessPhase"),
        BET: document.getElementById("betPhase"),
        REVEAL: document.getElementById("revealPhase")
    };
    const clueInput = document.getElementById("clueInput");
    const betLeft = document.getElementById("betLeft");
    const betRight = document.getElementById("betRight");
    const revealText = document.getElementById("revealText");
    const nextRound = document.getElementById("nextRound");

    let state = null;
    let dragging = false;
    let nudging = false;
    let nudgeDelayTimer = null;
    let nudgeRepeatTimer = null;
    let shownPointer = 50; // what the needle currently shows; leads the server while adjusting
    let lastSent = 0;
    let wasRevealed = false;
    let clueInputKey = null; // round the clue box was last cleared for

    const teamName = (t) => (t === "RED" ? "Vermelho" : "Azul");
    const isHost = () => R.role === "HOST";

    // ---------- Dragging the needle (master only) ----------
    function valueFromEvent(e) {
        // Padding box, not border box: the needle sits at a percentage of it, so the dial's
        // border must not shift where a touch lands.
        const width = dial.clientWidth;
        if (!width) return 50;
        const left = dial.getBoundingClientRect().left + dial.clientLeft;
        const ratio = (e.clientX - left) / width;
        return Math.max(0, Math.min(100, Math.round(ratio * 100)));
    }

    function canDrag() {
        return isHost() && state && state.canControl && state.phase === "GUESS" &&
            state.status === "PLAYING";
    }

    // Throttled while adjusting; every interaction force-flushes at the end, so a swallowed
    // update can't leave the server behind.
    function sendPointer(value, force) {
        const now = Date.now();
        if (!force && now - lastSent < POINTER_THROTTLE_MS) return;
        lastSent = now;
        R.send({ action: "setPointer", value: value });
    }

    /** True while the master is adjusting, so server echoes don't fight the local needle. */
    function adjusting() {
        return dragging || nudging;
    }

    function onDragStart(e) {
        if (!canDrag()) return;
        e.preventDefault();
        dragging = true;
        dial.classList.add("dragging");
        dial.setPointerCapture(e.pointerId);
        showPointer(valueFromEvent(e));
        sendPointer(shownPointer, true);
    }

    function onDragMove(e) {
        if (!dragging) return;
        showPointer(valueFromEvent(e)); // optimistic: don't wait for the round trip
        sendPointer(shownPointer, false);
    }

    function onDragEnd(e) {
        if (!dragging) return;
        dragging = false;
        dial.classList.remove("dragging");
        if (dial.releasePointerCapture && e.pointerId !== undefined) {
            try { dial.releasePointerCapture(e.pointerId); } catch (err) { /* already released */ }
        }
        // Don't recompute from the event: a pointercancel can carry stale coordinates.
        sendPointer(shownPointer, true);
    }

    // ---------- Stepping the needle with the arrows ----------
    // Dragging a thin bar is imprecise: one unit per tap, repeating while held.
    function startNudge(delta) {
        if (nudging || !canDrag()) return;
        nudging = true;
        dial.classList.add("dragging"); // drop the easing so repeats track the taps
        stepNudge(delta);
        nudgeDelayTimer = setTimeout(() => {
            nudgeRepeatTimer = setInterval(() => stepNudge(delta), NUDGE_REPEAT_MS);
        }, NUDGE_DELAY_MS);
    }

    function stepNudge(delta) {
        const next = shownPointer + delta;
        if (next < 0 || next > 100) return;
        showPointer(next);
        sendPointer(shownPointer, false);
    }

    function stopNudge() {
        if (!nudging) return;
        clearTimeout(nudgeDelayTimer);
        clearInterval(nudgeRepeatTimer);
        nudgeDelayTimer = null;
        nudgeRepeatTimer = null;
        nudging = false;
        dial.classList.remove("dragging");
        sendPointer(shownPointer, true); // flush whatever the throttle swallowed
    }

    function wireNudge(btn, delta) {
        btn.addEventListener("pointerdown", (e) => {
            e.preventDefault();
            startNudge(delta);
        });
        btn.addEventListener("contextmenu", (e) => e.preventDefault());
    }

    dial.addEventListener("pointerdown", onDragStart);
    dial.addEventListener("pointermove", onDragMove);
    dial.addEventListener("pointerup", onDragEnd);
    dial.addEventListener("pointercancel", onDragEnd);
    dial.addEventListener("contextmenu", (e) => e.preventDefault());

    function showPointer(value) {
        shownPointer = Math.max(0, Math.min(100, value));
        needle.style.left = shownPointer + "%";
    }

    // ---------- Rendering ----------
    function render(next) {
        state = next;
        leftLabel.textContent = next.left;
        rightLabel.textContent = next.right;
        clue.textContent = next.clue;
        scoreRed.textContent = next.scoreRed;
        scoreBlue.textContent = next.scoreBlue;
        roundLabel.textContent = `Rodada ${next.round} • até ${next.winningScore}`;

        // While the master adjusts, the local needle is authoritative — echoes would fight it.
        if (!adjusting()) showPointer(next.pointer);

        dial.classList.toggle("draggable", canDrag());
        renderTarget(next);
        renderResult(next);
        renderBanner(next);
        renderControls(next);
    }

    function renderResult(s) {
        if (s.phase !== "REVEAL") {
            result.classList.add("hidden");
            return;
        }
        const guess = s.guessingTeam === "RED" ? "pts-red" : "pts-blue";
        const bet = s.bettingTeam === "RED" ? "pts-red" : "pts-blue";
        result.innerHTML =
            `<span class="${guess}">${teamName(s.guessingTeam)} +${s.lastGuessPoints}</span>` +
            ` &nbsp;•&nbsp; ` +
            `<span class="${bet}">${teamName(s.bettingTeam)} +${s.lastBetPoints}</span>`;
        result.classList.remove("hidden");
    }

    function renderTarget(s) {
        if (s.target === null || s.target === undefined) {
            target.classList.add("hidden");
            wasRevealed = false;
            return;
        }
        const [inner, mid, outer] = s.bands; // half-widths, e.g. 2 / 6 / 10
        const widths = [outer - mid, mid - inner, inner * 2, mid - inner, outer - mid];
        const classes = ["p2", "p3", "p4", "p3", "p2"];
        const total = outer * 2;

        if (target.childElementCount !== widths.length) {
            target.innerHTML = "";
            widths.forEach((w, i) => {
                const band = document.createElement("span");
                band.className = "band " + classes[i];
                band.style.width = (w / total) * 100 + "%";
                target.appendChild(band);
            });
        }
        target.style.left = s.target - outer + "%";
        target.style.width = total + "%";
        target.classList.remove("hidden");

        // Replay the fade-in only on the transition into the reveal, not on every state push.
        const revealing = s.phase === "REVEAL";
        if (revealing && !wasRevealed) {
            target.classList.remove("revealing");
            void target.offsetWidth; // restart the animation
            target.classList.add("revealing");
        }
        wasRevealed = revealing;
    }

    function renderBanner(s) {
        banner.className = "banner";
        if (s.status === "RED_WINS" || s.status === "BLUE_WINS") {
            const winner = s.status === "RED_WINS" ? "RED" : "BLUE";
            banner.textContent = `🏆 ${teamName(winner)} venceu!`;
            banner.classList.add(winner === "RED" ? "win-red" : "win-blue");
            return;
        }
        const guessing = s.guessingTeam === "RED" ? "turn-red" : "turn-blue";
        switch (s.phase) {
            case "CLUE":
                banner.textContent = "Aguardando a dica";
                break;
            case "GUESS":
                banner.textContent = `${teamName(s.guessingTeam)} adivinha`;
                banner.classList.add(guessing);
                break;
            case "BET":
                banner.textContent = `${teamName(s.bettingTeam)} aposta o lado`;
                banner.classList.add(s.bettingTeam === "RED" ? "turn-red" : "turn-blue");
                break;
            case "REVEAL":
                banner.textContent = "Revelado!";
                banner.classList.add(guessing);
                break;
        }
    }

    function renderControls(s) {
        if (!s.canControl) return;
        controls.classList.remove("hidden");
        const over = s.status !== "PLAYING";
        const activePhase = over ? null : s.phase;
        Object.keys(phases).forEach((key) => {
            phases[key].classList.toggle("hidden", key !== activePhase);
        });

        if (activePhase === "CLUE") {
            // Clear once per round, so a reconnect mid-typing doesn't wipe what's in the box.
            if (clueInputKey !== s.round) {
                clueInput.value = "";
                clueInputKey = s.round;
            }
        } else if (activePhase === "BET") {
            betLeft.textContent = `◀ mais ${s.left}`;
            betRight.textContent = `mais ${s.right} ▶`;
        } else if (activePhase === "REVEAL") {
            revealText.textContent = `Alvo em ${s.target}`;
        }
        // Match over: every phase hides, leaving only "nova partida" in the top bar.
    }

    // ---------- Master actions ----------
    function wireHost() {
        hostControls.classList.remove("hidden");
        backBtn.classList.remove("hidden");
        backBtn.addEventListener("click", () => R.goBack());

        document.getElementById("newGame").addEventListener("click", () => {
            if (confirm("Iniciar uma nova partida?")) R.send({ action: "newGame" });
        });
        document.getElementById("sendClue").addEventListener("click", () => {
            R.send({ action: "setClue", text: clueInput.value });
        });
        clueInput.addEventListener("keydown", (e) => {
            if (e.key === "Enter") R.send({ action: "setClue", text: clueInput.value });
        });
        document.getElementById("lockGuess").addEventListener("click", () => {
            R.send({ action: "lockGuess" });
        });
        wireNudge(document.getElementById("nudgeLeft"), -NUDGE_STEP);
        wireNudge(document.getElementById("nudgeRight"), NUDGE_STEP);
        // Released anywhere (finger slid off the button, window lost focus): always stop.
        window.addEventListener("pointerup", stopNudge);
        window.addEventListener("pointercancel", stopNudge);
        window.addEventListener("blur", stopNudge);
        betLeft.addEventListener("click", () => R.send({ action: "setBet", side: "LEFT" }));
        betRight.addEventListener("click", () => R.send({ action: "setBet", side: "RIGHT" }));
        nextRound.addEventListener("click", () => R.send({ action: "nextRound" }));
    }

    function setConn(text, isError) {
        if (text) {
            banner.textContent = text;
            banner.className = "banner" + (isError ? " conn-error" : "");
        }
    }

    // ---------- Boot ----------
    // Everyone but the master is a spectator, so there is no landing screen to fill in.
    R.trackViewport(game);
    R.wireFullscreen(document.getElementById("fsBtn"));
    if (isHost()) wireHost();
    R.start(GAME_ID, { onState: render, onConn: setConn });

    if (!isHost()) {
        // Players still need a gesture to keep their screen awake and go fullscreen.
        document.addEventListener("click", function once() {
            R.enableKeepAwake();
            R.requestFullscreen();
            document.removeEventListener("click", once);
        });
    }
})();
