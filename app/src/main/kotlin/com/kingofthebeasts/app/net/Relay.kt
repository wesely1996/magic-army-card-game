package com.kingofthebeasts.app.net

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.random.Random

/**
 * Playing over the internet goes through a small relay server (see `server/` in the repository):
 * both phones connect to it with the same room code and it passes their messages along.
 */
object Relay {
    /** The relay this build talks to; Settings can point the app at another one. */
    const val DEFAULT_URL = ""

    /** Letters and digits that can't be mistaken for each other (no 0/O, 1/I). */
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    const val CODE_LENGTH = 5

    fun newCode(random: Random = Random): String = String(CharArray(CODE_LENGTH) { ALPHABET[random.nextInt(ALPHABET.length)] })

    /** A typed code, cleaned up: upper case, without spaces, dashes or letters codes never use. */
    fun clean(typed: String): String = typed.uppercase().filter { it in ALPHABET }.take(CODE_LENGTH)

    fun isCode(code: String): Boolean = code.length == CODE_LENGTH && code.all { it in ALPHABET }

    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            // WebSocket pings notice a dead connection (e.g. the phone switched networks).
            .pingInterval(15, TimeUnit.SECONDS)
            .build()
    }
}

/** The relay turned the phone away (no such game, code taken, game full), or couldn't be reached. */
class RelayRefused(val code: Int, message: String) : IOException(message) {
    companion object {
        const val NO_GAME = 4404
        const val TAKEN = 4409
        const val UNREACHABLE = -1
    }
}

/**
 * A [Link] through the relay. Connect with [RelayLink.connect], which returns once both players are
 * in the room. If the other player's connection closes the relay says so and this link closes too,
 * so the session waits for a new one exactly as it does when a Wi-Fi connection drops.
 */
class RelayLink private constructor() : Link {
    private val input = Channel<String>(Channel.UNLIMITED)
    override val lines: ReceiveChannel<String> get() = input
    private val paired = CompletableDeferred<Unit>()
    private var socket: WebSocket? = null
    private var onWaiting: () -> Unit = {}

    private val listener = object : WebSocketListener() {
        override fun onMessage(webSocket: WebSocket, text: String) {
            when (text) {
                PAIRED -> paired.complete(Unit)
                WAITING -> onWaiting()
                LEFT -> {
                    // The friend's connection dropped; they come back on a new link.
                    input.close()
                    webSocket.close(1000, null)
                }
                else -> input.trySend(text)
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            ended(RelayRefused(code, reason.ifBlank { "The game server closed the connection." }))
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            ended(RelayRefused(code, reason))
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            ended(RelayRefused(RelayRefused.UNREACHABLE, "Couldn't reach the game server. Check your internet connection."))
        }
    }

    private fun ended(why: RelayRefused) {
        paired.completeExceptionally(why)
        input.close()
    }

    override fun send(line: String) {
        socket?.send(line)
    }

    override fun close() {
        socket?.close(1000, null)
        input.close()
    }

    companion object {
        private const val PAIRED = """{"relay":"paired"}"""
        private const val WAITING = """{"relay":"waiting"}"""
        private const val LEFT = """{"relay":"left"}"""

        /**
         * Joins room [code] at the relay [url] as the host or the guest and waits until the other
         * player is there too. [fresh] is a first visit (the relay checks the code); without it the
         * phone is coming back to a battle. Throws [RelayRefused] if turned away or unreachable.
         */
        suspend fun connect(
            url: String, code: String, host: Boolean, fresh: Boolean,
            client: OkHttpClient = Relay.client, onWaiting: () -> Unit = {},
        ): RelayLink {
            val link = RelayLink()
            link.onWaiting = onWaiting
            val role = if (host) "host" else "guest"
            val request = runCatching {
                Request.Builder().url("${url.trimEnd('/')}/room/$code?role=$role" + if (fresh) "&new=1" else "").build()
            }.getOrElse { throw RelayRefused(RelayRefused.UNREACHABLE, "The game server address isn't valid.") }
            link.socket = client.newWebSocket(request, link.listener)
            try {
                link.paired.await()
            } catch (e: CancellationException) {
                link.close()
                throw e
            }
            return link
        }
    }
}
