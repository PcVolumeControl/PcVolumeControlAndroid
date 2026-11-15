package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IconProtocolTest {

    @Test
    fun `valid ids`() {
        for (id in listOf("a", "0", "demo1d94c5a", "4b13f0d55a3779a6ff892414f951be3f", "z".repeat(64))) {
            assertTrue(id, IconProtocol.isValidId(id))
        }
    }

    @Test
    fun `invalid ids`() {
        val invalid = listOf(
            null, "", "z".repeat(65), "ABC", "abc-def", "abc_def", "abc.png", "../x", "a/b", "a b",
            "abc\n", "été", "abc\u0000"
        )
        for (id in invalid) {
            assertFalse(id.toString(), IconProtocol.isValidId(id))
        }
    }

    @Test
    fun `request size is rounded physical pixels clamped to the protocol range`() {
        assertEquals(84, IconProtocol.requestSize(32f, 2.625f))
        assertEquals(96, IconProtocol.requestSize(32f, 3f))
        assertEquals(43, IconProtocol.requestSize(42.5f))
        assertEquals(16, IconProtocol.requestSize(8f, 1f))
        assertEquals(256, IconProtocol.requestSize(100f, 4f))
    }

    @Test
    fun `icon request serializes as the protocol expects`() {
        // The icon extension keeps the wire version at 7, so this pins the literal.
        val json = Gson().toJson(IconRequestMessage(IconRequest(listOf("a1", "b2"), 96)))

        assertEquals("""{"iconRequest":{"ids":["a1","b2"],"size":96},"protocolVersion":7}""", json)
    }
}
