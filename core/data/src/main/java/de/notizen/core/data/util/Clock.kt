package de.notizen.core.data.util

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Zeitquelle. Alle Zeitstempel im Projekt sind UTC-Millis.
 *
 * Existiert, damit Tests die Zeit kontrollieren koennen. Die
 * Konfliktaufloesung haengt an Zeitstempeln -- Szenarien wie "beide Clients
 * haben seit dem letzten Abgleich geaendert" lassen sich mit
 * `System.currentTimeMillis()` nicht zuverlaessig nachstellen.
 */
fun interface Clock {
    fun now(): Long
}

@Singleton
class SystemClock @Inject constructor() : Clock {
    override fun now(): Long = System.currentTimeMillis()
}

/** Testdoppel: steht still, bis es weitergestellt wird. */
class FixedClock(private var current: Long = 0L) : Clock {
    override fun now(): Long = current

    fun advanceBy(millis: Long) {
        current += millis
    }

    fun set(millis: Long) {
        current = millis
    }
}
