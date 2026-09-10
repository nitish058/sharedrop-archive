package org.nitish.project.sharedrop

import kotlin.time.Clock

class TransferSpeedTracker(
    private val minIntervalMs: Long = 400L,
    private val alpha: Double = 0.25
) {
    private var lastBytes = 0L
    private var lastTime = 0L
    private var smoothed = 0.0

    fun reset() {
        lastBytes = 0L
        lastTime = 0L
        smoothed = 0.0
    }

    /** Returns the updated smoothed speed (bytes/sec), or null if not enough time has passed to sample yet. */
    fun update(receivedBytes: Long): Double? {
        val now = Clock.System.now().toEpochMilliseconds()

        if (lastTime == 0L) {
            lastTime = now
            lastBytes = receivedBytes
            return null
        }

        val timeDelta = now - lastTime
        if (timeDelta < minIntervalMs) return null // too soon, skip — this is what kills the jitter

        val bytesDelta = (receivedBytes - lastBytes).coerceAtLeast(0L)
        val instantSpeed = bytesDelta.toDouble() / (timeDelta / 1000.0)

        smoothed = if (smoothed == 0.0) instantSpeed
        else alpha * instantSpeed + (1 - alpha) * smoothed

        lastBytes = receivedBytes
        lastTime = now
        return smoothed
    }
}