package com.gabbzin.pontual.ui.today

import com.gabbzin.pontual.testutil.MutableClock
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ActionGateTest {

    private val clock = MutableClock(Instant.parse("2026-11-13T13:00:00Z"))
    private val gate = ActionGate({ clock.instant() }, Duration.ofMillis(1500))

    @Test
    fun secondAcquire_rejectedWhileInFlight() {
        assertTrue(gate.tryAcquire())
        assertFalse(gate.tryAcquire())
    }

    @Test
    fun afterRelease_withinCooldown_rejected_thenAcceptedAfterCooldown() {
        assertTrue(gate.tryAcquire())
        gate.release()

        clock.set(clock.instant().plusMillis(300))
        assertFalse(gate.tryAcquire())

        clock.set(clock.instant().plusMillis(1200))
        assertTrue(gate.tryAcquire())
    }

    @Test
    fun zeroCooldown_acceptedImmediatelyAfterRelease() {
        val noCooldown = ActionGate({ clock.instant() })
        assertTrue(noCooldown.tryAcquire())
        noCooldown.release()
        assertTrue(noCooldown.tryAcquire())
    }
}
