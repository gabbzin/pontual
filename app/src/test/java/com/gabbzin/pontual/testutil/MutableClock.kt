package com.gabbzin.pontual.testutil

import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

class MutableClock(initial: Instant = Instant.EPOCH) : Clock() {

    @Volatile
    private var current: Instant = initial

    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = current

    fun set(instant: Instant) {
        current = instant
    }
}
