package io.github.sagakt.core

import kotlin.math.pow
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

sealed interface RetryPolicy {
    /** Returns the delay before the next attempt, or null to stop retrying. */
    fun delayFor(attempt: Int): Duration?

    data object None : RetryPolicy {
        override fun delayFor(attempt: Int): Duration? = null
    }

    data class Fixed(val maxAttempts: Int, val delay: Duration) : RetryPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be positive" }
        }

        override fun delayFor(attempt: Int): Duration? =
            if (attempt < maxAttempts) delay else null
    }

    data class Exponential(
        val maxAttempts: Int,
        val initial: Duration,
        val multiplier: Double = 2.0,
        val max: Duration = 30.seconds,
        val jitter: Double = 0.0,
    ) : RetryPolicy {
        init {
            require(maxAttempts > 0) { "maxAttempts must be positive" }
            require(multiplier >= 1.0) { "multiplier must be >= 1.0" }
            require(jitter in 0.0..1.0) { "jitter must be in [0, 1]" }
        }

        override fun delayFor(attempt: Int): Duration? {
            if (attempt >= maxAttempts) return null
            val raw = initial.inWholeMilliseconds * multiplier.pow(attempt)
            val capped = minOf(raw, max.inWholeMilliseconds.toDouble())
            val jittered = if (jitter == 0.0) {
                capped
            } else {
                val delta = capped * jitter
                capped + (Math.random() * 2 - 1) * delta
            }
            return jittered.toLong().coerceAtLeast(0).milliseconds
        }
    }
}
