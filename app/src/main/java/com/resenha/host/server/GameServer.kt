package com.resenha.host.server

import android.content.res.AssetManager
import com.resenha.host.server.codenames.CodenamesGame
import com.resenha.host.server.codenames.GameMode
import com.resenha.host.server.sintonia.SintoniaGame
import com.resenha.host.server.sintonia.SintoniaMode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.*
import io.ktor.server.cio.CIO
import io.ktor.server.engine.ApplicationEngine
import io.ktor.server.engine.embeddedServer
import io.ktor.server.request.*
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.DefaultWebSocketServerSession
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP + WebSocket server (Ktor, CIO). Keeps one game active at a time, serves its web
 * client from assets, and pushes role-filtered state to every client. Only the HOST may mutate.
 */
class GameServer(
    private val assets: AssetManager,
    val port: Int = DEFAULT_PORT
) {
    private val codenames = CodenamesGame(assets)
    private val sintonia = SintoniaGame(assets)
    private val games: Map<String, HostedGame> =
        listOf(codenames, sintonia).associateBy { it.id }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var server: ApplicationEngine? = null

    private class Client(val session: DefaultWebSocketServerSession, val role: Role)
    private val clients = CopyOnWriteArrayList<Client>()

    private val _playerCount = MutableStateFlow(0)
    val playerCount: StateFlow<Int> = _playerCount

    private val _activeGame = MutableStateFlow(CodenamesGame.ID)
    val activeGame: StateFlow<String> = _activeGame

    val codenamesMode: StateFlow<GameMode> = codenames.mode
    val sintoniaMode: StateFlow<SintoniaMode> = sintonia.mode

    private val active: HostedGame
        get() = games.getValue(_activeGame.value)

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                webSocket("/ws") { handleClient() }
                get("/") { respondAsset(call, "") }
                get("/{path...}") {
                    val path = call.parameters.getAll("path")?.joinToString("/").orEmpty()
                    respondAsset(call, path)
                }
            }
        }.start(wait = false)
    }

    fun stop() {
        server?.stop(GRACE_MS, TIMEOUT_MS)
        server = null
        job.cancel()
    }

    /** Switches the hosted game. Connected browsers see the new id and follow on their own. */
    fun selectGame(id: String) {
        if (!games.containsKey(id)) return
        mutate { _activeGame.value = id }
    }

    /** Switches the word theme and deals a fresh board. Called from the UI. */
    fun setCodenamesMode(mode: GameMode) = mutate { codenames.setMode(mode) }

    /** Switches the spectrum deck and starts a fresh match. Called from the UI. */
    fun setSintoniaMode(mode: SintoniaMode) = mutate { sintonia.setMode(mode) }

    /** Runs a state change under the lock and pushes the result. Safe to call from the UI thread. */
    private fun mutate(block: () -> Unit) {
        scope.launch {
            stateMutex.withLock { block() }
            broadcast()
        }
    }

    private suspend fun DefaultWebSocketServerSession.handleClient() {
        val role = Role.from(call.request.queryParameters["role"])
        val client = Client(this, role)
        clients.add(client)
        updatePlayerCount()
        try {
            sendStateTo(client)
            for (frame in incoming) {
                if (frame !is Frame.Text) continue
                val action = runCatching { json.decodeFromString<ActionDto>(frame.readText()) }
                    .getOrNull() ?: continue
                if (client.role != Role.HOST) continue // only the host may mutate state
                stateMutex.withLock { active.handle(action) }
                broadcast()
            }
        } finally {
            clients.remove(client)
            updatePlayerCount()
        }
    }

    private suspend fun sendStateTo(client: Client) {
        val text = stateMutex.withLock { active.snapshotJson(client.role) }
        runCatching { client.session.send(Frame.Text(text)) }
    }

    private suspend fun broadcast() {
        for (client in clients) sendStateTo(client)
    }

    private fun updatePlayerCount() {
        _playerCount.value = clients.count { it.role != Role.HOST }
    }

    /**
     * Serves `assets/web/`, one folder per game. A miss on something that looks like a file is a
     * real 404; anything else falls back to that folder's `index.html`, so `/sintonia` and
     * `/sintonia/` both open the game.
     */
    private suspend fun respondAsset(call: ApplicationCall, rawPath: String) {
        val name = rawPath.substringBefore('?').trim('/')
        if (name.contains("..")) {
            call.respondBytes(ByteArray(0), status = HttpStatusCode.Forbidden)
            return
        }
        if (name.isEmpty()) {
            respondBytesOr404(call, readAsset("web/index.html"), "index.html")
            return
        }

        val direct = readAsset("web/$name")
        if (direct != null) {
            call.respondBytes(direct, contentTypeFor(name))
            return
        }
        if (looksLikeFile(name)) {
            call.respondBytes(ByteArray(0), status = HttpStatusCode.NotFound)
            return
        }
        val folderIndex = readAsset("web/${name.substringBefore('/')}/index.html")
        respondBytesOr404(call, folderIndex ?: readAsset("web/index.html"), "index.html")
    }

    private suspend fun respondBytesOr404(call: ApplicationCall, bytes: ByteArray?, name: String) {
        if (bytes == null) call.respondBytes(ByteArray(0), status = HttpStatusCode.NotFound)
        else call.respondBytes(bytes, contentTypeFor(name))
    }

    private fun looksLikeFile(name: String): Boolean =
        name.substringAfterLast('/').substringAfterLast('.', "").isNotEmpty()

    private fun readAsset(path: String): ByteArray? =
        runCatching { assets.open(path).use { it.readBytes() } }.getOrNull()

    private fun contentTypeFor(name: String): ContentType = when {
        name.endsWith(".html") -> ContentType.Text.Html
        name.endsWith(".js") -> ContentType.Text.JavaScript
        name.endsWith(".css") -> ContentType.Text.CSS
        name.endsWith(".json") -> ContentType.Application.Json
        name.endsWith(".svg") -> ContentType.Image.SVG
        name.endsWith(".png") -> ContentType.Image.PNG
        else -> ContentType.Application.OctetStream
    }

    companion object {
        const val DEFAULT_PORT = 8080
        private const val GRACE_MS = 300L
        private const val TIMEOUT_MS = 1000L
    }
}
