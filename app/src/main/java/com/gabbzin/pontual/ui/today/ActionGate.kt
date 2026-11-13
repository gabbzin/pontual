package com.gabbzin.pontual.ui.today

import java.time.Duration
import java.time.Instant

/**
 * Trava de ação: uma execução por vez e, depois de terminar, um período de [cooldown]
 * em que novas tentativas são recusadas. Sem Android, tempo injetado por [now].
 */
class ActionGate(
    private val now: () -> Instant,
    val cooldown: Duration = Duration.ZERO,
) {
    private var inFlight = false
    private var lastEnd: Instant? = null

    fun isBusy(): Boolean = inFlight || lastEnd?.let { now() < it.plus(cooldown) } == true

    fun tryAcquire(): Boolean {
        if (isBusy()) return false
        inFlight = true
        return true
    }

    fun release() {
        inFlight = false
        lastEnd = now()
    }
}
