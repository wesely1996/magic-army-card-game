package com.kingofthebeasts.app

import com.kingofthebeasts.app.net.Relay
import com.kingofthebeasts.app.net.RelayLink
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assume
import org.junit.Test

class ProbeLiveRelay {
    @Test fun hostWaitsLong() = runBlocking {
        val url = System.getenv("RELAY_URL")
        Assume.assumeTrue(!url.isNullOrBlank())
        val code = Relay.newCode()
        val t0 = System.currentTimeMillis()
        fun t() = (System.currentTimeMillis() - t0) / 1000.0
        var waited = false
        val host = async { RelayLink.connect(url!!, code, host = true, fresh = true, onWaiting = { waited = true; println("PROBE ${t()} host waiting") }) }
        while (!waited) delay(50)
        val wait = (System.getenv("PROBE_WAIT") ?: "50").toLong()
        delay(wait * 1000)
        println("PROBE ${t()} guest joins (host active=${host.isActive})")
        val guest = runCatching { RelayLink.connect(url!!, code, host = false, fresh = true) }
        println("PROBE ${t()} guest result: ${guest.exceptionOrNull() ?: "paired"}")
        val h = runCatching { withTimeout(5000) { host.await() } }
        println("PROBE ${t()} host result: ${h.exceptionOrNull() ?: "paired"}")
        val hl = h.getOrNull(); val gl = guest.getOrNull()
        if (hl != null && gl != null) {
            gl.send("hello-from-guest")
            println("PROBE host got: " + runCatching { withTimeout(5000) { hl.lines.receive() } })
            delay(40_000)
            gl.send("after-40s")
            println("PROBE ${t()} host got after idle: " + runCatching { withTimeout(5000) { hl.lines.receive() } })
        }
    }
}
