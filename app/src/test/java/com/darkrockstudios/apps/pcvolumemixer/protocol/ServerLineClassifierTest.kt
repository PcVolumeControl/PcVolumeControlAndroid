package com.darkrockstudios.apps.pcvolumemixer.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerLineClassifierTest {
    private val classifier = ServerLineClassifier()

    @Test
    fun `v7 state is a state`() {
        val line = classifier.classify(Samples.stateV7)

        assertTrue(line is ServerLine.State)
        assertEquals(7, (line as ServerLine.State).state.protocolVersion)
    }

    @Test
    fun `v8 state is a state`() {
        val line = classifier.classify(Samples.stateWithIcons) as ServerLine.State

        assertEquals(listOf("icons"), line.state.features)
    }

    @Test
    fun `icon image reply is an icon and never a state`() {
        val line = classifier.classify(Samples.iconImage)

        assertTrue(line is ServerLine.Icon)
        val reply = (line as ServerLine.Icon).reply as IconReply.Image
        assertEquals("demo1d94c5a", reply.id)
        assertEquals(32, reply.size)
        assertEquals("image/png", reply.mime)
        val png = reply.decodePng()
        assertNotNull(png)
        val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)
        assertArrayEquals(pngSignature, png!!.copyOf(8))
    }

    @Test
    fun `icon not found reply`() {
        val line = classifier.classify(Samples.iconNotFound) as ServerLine.Icon

        assertEquals(IconReply.Missing("zzzznotarealid", "notFound"), line.reply)
    }

    @Test
    fun `unknown error value is still a missing icon`() {
        val line = classifier.classify("""{"protocolVersion":7,"icon":{"id":"abc","error":"tooBig"}}""")

        assertEquals(ServerLine.Icon(IconReply.Missing("abc", "tooBig")), line)
    }

    @Test
    fun `line with neither key is unknown`() {
        assertSame(ServerLine.Unknown, classifier.classify("""{"protocolVersion":7,"somethingNew":{"a":1}}"""))
        assertSame(ServerLine.Unknown, classifier.classify("""{"protocolVersion":7}"""))
    }

    @Test
    fun `null default device is not a state`() {
        assertSame(ServerLine.Unknown, classifier.classify("""{"protocolVersion":7,"defaultDevice":null}"""))
    }

    @Test
    fun `malformed lines are reported, not thrown`() {
        val malformed = listOf(
            "",
            "not json",
            "{\"protocolVersion\":7,\"defaultDevice\":{",
            "[1,2,3]",
            "\"just a string\"",
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"d","sessions":"nope"}}""",
            """{"protocolVersion":7,"icon":"nope"}""",
            """{"protocolVersion":7,"icon":{"error":"notFound"}}""",
            """{"protocolVersion":7,"icon":{"id":"../etc/passwd","error":"notFound"}}""",
            """{"protocolVersion":7,"icon":{"id":"abc","size":32,"mime":"image/png"}}""",
        )

        for (text in malformed) {
            assertTrue("Expected malformed: $text", classifier.classify(text) is ServerLine.Malformed)
        }
    }

    @Test
    fun `invalid base64 decodes to null`() {
        val reply = IconReply.Image("abc", 32, "image/png", "***")

        assertEquals(null, reply.decodePng())
    }

    @Test
    fun `large icon line is classified`() {
        val data = "A".repeat(700 * 1024)
        val line = classifier.classify(
            """{"protocolVersion":7,"icon":{"id":"big","size":256,"mime":"image/png","data":"$data"}}"""
        ) as ServerLine.Icon

        assertEquals(700 * 1024 / 4 * 3, (line.reply as IconReply.Image).decodePng()!!.size)
    }
}
