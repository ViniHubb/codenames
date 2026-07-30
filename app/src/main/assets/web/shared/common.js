"use strict";

/**
 * Shared plumbing for every Resenha client: role parsing, the auto-reconnecting WebSocket,
 * fullscreen, keep-awake and the native bridge. Each app.js only renders state and sends actions.
 */
(function () {
    const params = new URLSearchParams(location.search);
    const role = (params.get("role") || "").toUpperCase();
    const team = (params.get("team") || "").toUpperCase();

    let ws = null;
    let gameId = null;
    let onState = null;
    let onConn = null;

    // ---------- Connection ----------
    function connect() {
        const proto = location.protocol === "https:" ? "wss" : "ws";
        const url = `${proto}://${location.host}/ws?role=${encodeURIComponent(currentRole())}&team=${encodeURIComponent(currentTeam())}`;
        ws = new WebSocket(url);
        ws.onopen = () => notifyConn("");
        ws.onmessage = (e) => {
            let msg;
            try {
                msg = JSON.parse(e.data);
            } catch (err) {
                return; // ignore malformed
            }
            if (msg.type !== "state") return;
            // The host switched games: follow along instead of rendering a foreign state.
            if (msg.game && gameId && msg.game !== gameId) {
                location.replace("/" + msg.game + "/" + location.search);
                return;
            }
            if (onState) onState(msg);
        };
        ws.onclose = () => {
            notifyConn("Reconectando…", true);
            setTimeout(connect, 1500);
        };
        ws.onerror = () => ws.close();
    }

    function notifyConn(text, isError) {
        if (onConn) onConn(text, !!isError);
    }

    // Games may let the player pick a role on a landing screen after load.
    let liveRole = role;
    let liveTeam = team;
    function currentRole() { return liveRole; }
    function currentTeam() { return liveTeam; }

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

    /** Wires a floating button to toggle fullscreen. Browser players only — the host is a WebView. */
    function wireFullscreen(btn) {
        if (!btn) return;
        if (currentRole() === "HOST") {
            btn.classList.add("hidden");
            return;
        }
        const updateIcon = () => {
            btn.textContent = isFullscreen() ? "✕" : "⛶";
            btn.title = isFullscreen() ? "Sair da tela cheia" : "Tela cheia";
        };
        btn.addEventListener("click", () => (isFullscreen() ? exitFullscreen() : requestFullscreen()));
        document.addEventListener("fullscreenchange", updateIcon);
        document.addEventListener("webkitfullscreenchange", updateIcon);
    }

    // ---------- Keep awake (players) ----------
    // Wake Lock needs https, so NoSleep.js falls back to a hidden video. Needs a user gesture.
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

    // ---------- Viewport ----------
    // Some Android WebViews resolve percentage heights unreliably, leaving flex children tiny.
    // Pin the measured height instead, and recompute on resize/rotation.
    function fitViewport(el) {
        if (!el) return;
        const h = window.innerHeight || document.documentElement.clientHeight;
        if (h) el.style.height = h + "px";
    }

    function trackViewport(el) {
        const apply = () => fitViewport(el);
        apply();
        window.addEventListener("resize", apply);
        window.addEventListener("orientationchange", () => setTimeout(apply, 150));
    }

    // ---------- Native bridge ----------
    function goBack() {
        if (window.AndroidHost && window.AndroidHost.back) window.AndroidHost.back();
    }

    window.Resenha = {
        get role() { return currentRole(); },
        get team() { return currentTeam(); },
        /** Overrides the role/team picked on a landing screen, before start(). */
        setIdentity(newRole, newTeam) {
            if (newRole) liveRole = newRole.toUpperCase();
            if (newTeam) liveTeam = newTeam.toUpperCase();
        },
        /** Opens the connection for `id`, calling `handlers.onState` on every snapshot. */
        start(id, handlers) {
            gameId = id;
            onState = handlers && handlers.onState;
            onConn = handlers && handlers.onConn;
            connect();
        },
        send(obj) {
            if (ws && ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(obj));
        },
        requestFullscreen,
        wireFullscreen,
        enableKeepAwake,
        trackViewport,
        goBack
    };
})();
