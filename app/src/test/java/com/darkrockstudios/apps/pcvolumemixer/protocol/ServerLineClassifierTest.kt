package com.darkrockstudios.apps.pcvolumemixer.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class ServerLineClassifierTest {
    private val classifier = ServerLineClassifier()

    private val pngSignature = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

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
        val png = reply.decodePng()
        assertNotNull(png)
        assertEquals(424, png!!.size)
        assertArrayEquals(pngSignature, png.copyOf(8))
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
    fun `unusable image reply with a valid id still answers that id`() {
        val unusable = listOf(
            """{"id":"abc","size":32,"mime":"image/png"}""",
            """{"id":"abc","mime":"image/png","data":"AAAA"}""",
            """{"id":"abc","size":0,"mime":"image/png","data":"AAAA"}""",
            """{"id":"abc","size":257,"mime":"image/png","data":"AAAA"}""",
            """{"id":"abc","size":32,"mime":"image/svg+xml","data":"AAAA"}""",
            """{"id":"abc"}""",
        )

        for (icon in unusable) {
            assertEquals(
                icon,
                ServerLine.Icon(IconReply.Missing("abc", IconReply.ERROR_INVALID)),
                classifier.classify("""{"protocolVersion":7,"icon":$icon}""")
            )
        }
    }

    @Test
    fun `line with neither key is unknown`() {
        assertSame(ServerLine.Unknown, classifier.classify("""{"protocolVersion":7,"somethingNew":{"a":1}}"""))
        assertSame(ServerLine.Unknown, classifier.classify("""{"protocolVersion":7}"""))
    }

    @Test
    fun `other protocol versions are reported whatever the line holds`() {
        assertEquals(
            ServerLine.VersionMismatch(8),
            classifier.classify("""{"protocolVersion":8,"defaultDevice":{"deviceId":"d"}}""")
        )
        assertEquals(ServerLine.VersionMismatch(6), classifier.classify("""{"protocolVersion":6,"somethingNew":1}"""))
        assertEquals(ServerLine.VersionMismatch(null), classifier.classify("""{"version":7,"defaultDevice":{"deviceId":"d"}}"""))
        assertEquals(ServerLine.VersionMismatch(null), classifier.classify("""{"protocolVersion":"seven"}"""))
    }

    @Test
    fun `integral float version is accepted`() {
        assertTrue(classifier.classify("""{"protocolVersion":7.0,"defaultDevice":{"deviceId":"d"}}""") is ServerLine.State)
    }

    @Test
    fun `malformed lines are reported, not thrown`() {
        val malformed = listOf(
            "",
            "not json",
            "{\"protocolVersion\":7,\"defaultDevice\":{",
            "[1,2,3]",
            "\"just a string\"",
            """{"protocolVersion":7,"defaultDevice":null}""",
            """{"protocolVersion":7,"defaultDevice":"x"}""",
            """{"protocolVersion":7,"defaultDevice":{"name":"no id"}}""",
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"d","sessions":"nope"}}""",
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"d","sessions":[null]}}""",
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"d","sessions":[{"volume":1,"muted":false}]}}""",
            """{"protocolVersion":7,"deviceIds":{"d":null},"defaultDevice":{"deviceId":"d"}}""",
            """{"protocolVersion":7,"features":{"icons":true},"defaultDevice":{"deviceId":"d"}}""",
            """{"protocolVersion":7,"icon":"nope"}""",
            """{"protocolVersion":7,"icon":{"error":"notFound"}}""",
            """{"protocolVersion":7,"icon":{"id":"../etc/passwd","error":"notFound"}}""",
        )

        for (text in malformed) {
            assertTrue("Expected malformed: $text", classifier.classify(text) is ServerLine.Malformed)
        }
    }

    @Test
    fun `invalid or non PNG data decodes to null`() {
        assertNull(IconReply.Image("abc", 32, "***").decodePng())
        assertNull(IconReply.Image("abc", 32, "").decodePng())
        assertNull(IconReply.Image("abc", 32, Base64.getEncoder().encodeToString("GIF89a..".toByteArray())).decodePng())
    }

    @Test
    fun `large icon line is classified`() {
        val png = pngSignature + ByteArray(512 * 1024)
        val data = Base64.getEncoder().encodeToString(png)
        val line = classifier.classify(
            """{"protocolVersion":7,"icon":{"id":"big","size":256,"mime":"image/png","data":"$data"}}"""
        ) as ServerLine.Icon

        assertEquals(png.size, (line.reply as IconReply.Image).decodePng()!!.size)
    }
}
