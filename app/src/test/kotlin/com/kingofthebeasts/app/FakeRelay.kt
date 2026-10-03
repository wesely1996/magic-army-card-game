package com.kingofthebeasts.app

import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest

/**
 * The relay server (server/src/index.js) in miniature, for tests: rooms of one host and one guest,
 * the same refusals and the same three relay messages.
 */
class FakeRelay : AutoCloseable {
    private class Room {
        val sockets = mutableMapOf<String, WebSocket>()
    }

    private val rooms = mutableMapOf<String, Room>()
    private val lock = Any()
    private val server = MockWebServer()

    /** How many connections have come in, to see the app reconnecting. */
    @Volatile var connections = 0
        private set

    val url: String get() = server.url("/").toString().trimEnd('/')

    init {
        server.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val url = request.requestUrl ?: return MockResponse().setResponseCode(400)
                val code = url.pathSegments.getOrNull(1)?.uppercase()
                val role = url.queryParameter("role")
                if (url.pathSegments.firstOrNull() != "room" || code == null || (role != "host" && role != "guest")) {
                    return MockResponse().setResponseCode(400)
                }
                val fresh = url.queryParameter("new") == "1"
                connections++
                return MockResponse().withWebSocketUpgrade(Player(code, role, fresh))
            }
        }
        server.start()
    }

    private inner class Player(val code: String, val role: String, val fresh: Boolean) : WebSocketListener() {
        private val other = if (role == "host") "guest" else "host"

        override fun onOpen(webSocket: WebSocket, response: Response) = synchronized(lock) {
            val room = rooms.getOrPut(code) { Room() }
            val mine = room.sockets[role]
            val theirs = room.sockets[other]
            val refusal = when {
                fresh && role == "host" && (mine != null || theirs != null) -> 4409 to "That code is taken."
                fresh && role == "guest" && theirs == null -> 4404 to "No game with that code is waiting."
                fresh && role == "guest" && mine != null -> 4409 to "That game already has two players."
                else -> null
            }
            if (refusal != null) {
                webSocket.close(refusal.first, refusal.second)
                return@synchronized
            }
            room.sockets[role] = webSocket
            mine?.close(4000, "Replaced by a new connection.")
            if (theirs != null) {
                webSocket.send(PAIRED)
                theirs.send(PAIRED)
            } else {
                webSocket.send(WAITING)
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) = synchronized(lock) {
            val room = rooms[code] ?: return@synchronized
            if (room.sockets[role] === webSocket) room.sockets[other]?.send(text)
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            gone(webSocket)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = gone(webSocket)

        private fun gone(webSocket: WebSocket) = synchronized(lock) {
            val room = rooms[code] ?: return@synchronized
            if (room.sockets[role] !== webSocket) return@synchronized // replaced, or refused
            room.sockets.remove(role)
            room.sockets[other]?.send(LEFT)
        }
    }

    /** Cuts [role]'s connection in room [code], like a phone losing its network. */
    fun drop(code: String, role: String) = synchronized(lock) {
        val room = rooms[code] ?: error("no such room")
        val ws = room.sockets.remove(role) ?: error("nobody there")
        room.sockets[if (role == "host") "guest" else "host"]?.send(LEFT)
        ws.close(1001, "Network lost")
    }

    override fun close() {
        runCatching { server.shutdown() }
    }

    companion object {
        const val PAIRED = """{"relay":"paired"}"""
        const val WAITING = """{"relay":"waiting"}"""
        const val LEFT = """{"relay":"left"}"""
    }
}
