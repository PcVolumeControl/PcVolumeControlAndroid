package com.darkrockstudios.apps.pcvolumemixer.icons

import kotlin.math.roundToInt

/** Size arithmetic for fitting a decoded icon into a square of `target` pixels. */
object IconScaling {

    /**
     * The largest power-of-two decode subsampling that keeps the image at least [target] pixels on
     * its longer side, so the filtered scale that follows reduces by at most 2x.
     */
    fun sampleSize(width: Int, height: Int, target: Int): Int {
        require(target > 0) { "target must be positive: $target" }
        val longer = maxOf(width, height)
        var sample = 1
        while (longer / (sample * 2) >= target) sample *= 2
        return sample
    }

    /** The dimensions that fit [width] x [height] within [target], or null when it already fits. */
    fun fitWithin(width: Int, height: Int, target: Int): Pair<Int, Int>? {
        require(target > 0) { "target must be positive: $target" }
        val longer = maxOf(width, height)
        if (longer <= target) return null
        val scale = target.toFloat() / longer
        return Pair(
            (width * scale).roundToInt().coerceIn(1, target),
            (height * scale).roundToInt().coerceIn(1, target)
        )
    }
}
