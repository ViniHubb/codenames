package com.codenames.host.server

import android.content.res.AssetManager
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Embedded HTTP + WebSocket server (Ktor, CIO engine). Holds the single authoritative
 * [GameEngine], serves the web client from assets, and pushes role-filtered state to every
 * connected client. Only the HOST connection may mutate game state.
 */
class GameServer(
    private val assets: AssetManager,
    val port: Int = DEFAULT_PORT
) {
    private val engine = GameEngine(WordBank.load(assets))
    private val job = SupervisorJob()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val stateMutex = Mutex()

    private var server: ApplicationEngine? = null

    private class Client(val session: DefaultWebSocketServerSession, val role: Role)
    private val clients = CopyOnWriteArrayList<Client>()

    private val _playerCount = MutableStateFlow(0)
    val playerCount: StateFlow<Int> = _playerCount

    fun start() {
        if (server != null) return
        server = embeddedServer(CIO, port = port, host = "0.0.0.0") {
            install(WebSockets)
            routing {
                webSocket("/ws") { handleClient() }
                get("/") { respondAsset(call, "index.html") }
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

    /** Deals a fresh board and broadcasts to all clients. Safe to call from the UI thread. */
    fun newGame() {
        scope.launch {
            stateMutex.withLock { engine.newGame() }
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
                stateMutex.withLock {
                    when (action.action) {
                        "reveal" -> action.index?.let { engine.reveal(it) }
                        "passTurn" -> engine.passTurn()
                        "newGame" -> engine.newGame()
                    }
                }
                broadcast()
            }
        } finally {
            clients.remove(client)
            updatePlayerCount()
        }
    }

    private suspend fun sendStateTo(client: Client) {
        val dto = stateMutex.withLock { engine.snapshotFor(client.role) }
        val text = json.encodeToString(StateDto.serializer(), dto)
        runCatching { client.session.send(Frame.Text(text)) }
    }

    private suspend fun broadcast() {
        for (client in clients) sendStateTo(client)
    }

    private fun updatePlayerCount() {
        _playerCount.value = clients.count { it.role != Role.HOST }
    }

    private suspend fun respondAsset(call: ApplicationCall, rawPath: String) {
        val clean = rawPath.substringBefore('?').trim('/')
        val name = if (clean.isEmpty()) "index.html" else clean
        if (name.contains("..")) {
            call.respondBytes(ByteArray(0), status = HttpStatusCode.Forbidden)
            return
        }
        val bytes = readAsset("web/$name") ?: readAsset("web/index.html") // SPA fallback
        if (bytes == null) {
            call.respondBytes(ByteArray(0), status = HttpStatusCode.NotFound)
            return
        }
        call.respondBytes(bytes, contentTypeFor(name))
    }

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
