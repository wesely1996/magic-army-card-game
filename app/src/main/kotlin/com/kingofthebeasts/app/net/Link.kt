package com.kingofthebeasts.app.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.Socket

/**
 * A two-way connection to the other player's app, one text line per message. Same-Wi-Fi play
 * uses [SocketLink]; another transport (e.g. over the internet) only has to provide this.
 */
interface Link {
    /** Lines from the other side; closes when the connection does. */
    val lines: ReceiveChannel<String>
    fun send(line: String)
    fun close()
}

/** A [Link] over a TCP socket. Reading and writing happen on background threads. */
class SocketLink(private val socket: Socket, scope: CoroutineScope) : Link {
    private val out = Channel<String>(Channel.UNLIMITED)
    private val input = Channel<String>(Channel.UNLIMITED)
    override val lines: ReceiveChannel<String> get() = input

    init {
        socket.tcpNoDelay = true
        socket.keepAlive = true
        scope.launch(Dispatchers.IO) {
            try {
                val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.isNotBlank()) input.send(line)
                }
            } catch (_: Exception) {
                // the connection dropped
            } finally {
                input.close()
                out.close()
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                val writer = OutputStreamWriter(socket.getOutputStream(), Charsets.UTF_8)
                for (line in out) {
                    writer.write(line)
                    writer.write("\n")
                    writer.flush()
                }
            } catch (_: Exception) {
                input.close()
            } finally {
                runCatching { socket.close() }
            }
        }
    }

    override fun send(line: String) {
        out.trySend(line)
    }

    override fun close() {
        out.close()
        runCatching { socket.shutdownInput() }
    }
}
