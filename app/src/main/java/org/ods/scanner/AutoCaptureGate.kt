package org.ods.scanner

import kotlin.math.abs

/** A held page may trigger once; it must leave or materially change before rearming. */
class AutoCaptureGate {
    private var previous: Crop? = null
    private var captured: Crop? = null
    private var stableSince = 0L
    fun update(crop: Crop?, sharp: Boolean, now: Long): Boolean {
        if (crop == null) { previous = null; captured = null; stableSince = now; return false }
        fun difference(a: Crop, b: Crop) = a.points.zip(b.points).maxOf { (p,q) -> maxOf(abs(p.x-q.x), abs(p.y-q.y)) }
        captured?.let { if (difference(it, crop) > .12f) captured = null }
        if (!sharp || previous == null || difference(previous!!,crop) > .015f) stableSince = now
        previous = crop
        if (sharp && captured == null && now - stableSince >= 600) { captured = crop; return true }
        return false
    }
}
