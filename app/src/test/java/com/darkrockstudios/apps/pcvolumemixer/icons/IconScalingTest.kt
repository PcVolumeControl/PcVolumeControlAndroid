package com.darkrockstudios.apps.pcvolumemixer.icons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IconScalingTest {

    @Test
    fun `sample size halves only while the result stays at least the target`() {
        assertEquals(1, IconScaling.sampleSize(84, 84, 84))
        assertEquals(1, IconScaling.sampleSize(160, 160, 84))
        assertEquals(2, IconScaling.sampleSize(168, 168, 84))
        assertEquals(2, IconScaling.sampleSize(256, 256, 84))
        assertEquals(4, IconScaling.sampleSize(256, 256, 64))
        assertEquals(8, IconScaling.sampleSize(256, 128, 32))
        assertEquals(1, IconScaling.sampleSize(16, 16, 84))
    }

    @Test
    fun `an image that already fits is not scaled`() {
        assertNull(IconScaling.fitWithin(84, 84, 84))
        assertNull(IconScaling.fitWithin(32, 64, 84))
    }

    @Test
    fun `scaling keeps the aspect ratio within the target`() {
        assertEquals(Pair(84, 84), IconScaling.fitWithin(128, 128, 84))
        assertEquals(Pair(84, 42), IconScaling.fitWithin(256, 128, 84))
        assertEquals(Pair(42, 84), IconScaling.fitWithin(100, 200, 84))
    }

    @Test
    fun `a very thin image keeps at least one pixel`() {
        assertEquals(Pair(16, 1), IconScaling.fitWithin(256, 1, 16))
    }
}
