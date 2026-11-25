package com.nthb.kibooz.ui.chat

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout

class ChatPollerTest {

    @get:Rule
    val timeout: Timeout = Timeout(30, TimeUnit.SECONDS)

    @Test
    fun `it waits the interval before every tick and the interval is five seconds`() = runBlocking {
        val events = mutableListOf<String>()
        var ticks = 0

        launch {
            ChatPoller(pause = { events += "pause $it" }).run {
                events += "tick"
                if (++ticks == 3) currentCoroutineContext().cancel()
            }
        }.join()

        assertEquals(5_000L, CHAT_POLL_INTERVAL_MILLIS)
        assertEquals(
            listOf("pause 5000", "tick", "pause 5000", "tick", "pause 5000", "tick"),
            events
        )
    }

    @Test
    fun `the next wait starts only after the tick has finished, so two polls never overlap`() = runBlocking {
        val events = mutableListOf<String>()
        var ticks = 0

        launch {
            ChatPoller(pause = { events += "pause" }).run {
                events += "tick start"
                yield()
                events += "tick end"
                if (++ticks == 2) currentCoroutineContext().cancel()
            }
        }.join()

        assertEquals(
            listOf("pause", "tick start", "tick end", "pause", "tick start", "tick end"),
            events
        )
    }

    @Test
    fun `it stops without a tick when it is cancelled while waiting`() = runBlocking {
        var ticks = 0

        val job = launch { ChatPoller().run { ticks++ } }
        yield()
        job.cancel()
        job.join()

        assertEquals(0, ticks)
    }
}
