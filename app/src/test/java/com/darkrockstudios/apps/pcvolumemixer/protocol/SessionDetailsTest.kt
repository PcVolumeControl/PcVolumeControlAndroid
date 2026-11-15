package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionDetailsTest {
    private val gson = Gson()

    private fun state(json: String): PcAudio = gson.fromJson(json, PcAudio::class.java)

    @Test
    fun `state with details decodes them per session`() {
        val sessions = state(Samples.stateWithDetails).defaultDevice.sessions!!

        assertEquals(
            listOf(null, null, null, null, "Radio Paradise - Main Mix", "Big Buck Bunny - YouTube"),
            sessions.map { it.detail }
        )
        assertEquals(
            listOf("Firefox", "Spotify", "Discord", "Musique \u00E9t\u00E9", "Firefox", "Firefox"),
            sessions.map { it.name }
        )
    }

    @Test
    fun `states without details decode with none`() {
        assertTrue(state(Samples.stateV7).defaultDevice.sessions!!.all { it.detail == null })
        assertTrue(state(Samples.stateWithIcons).defaultDevice.sessions!!.all { it.detail == null })
    }

    @Test
    fun `details need no feature name`() {
        val line = """{"protocolVersion":7,"defaultDevice":{"deviceId":"d","sessions":[""" +
            """{"name":"Firefox","id":"s","volume":1.0,"muted":false,"detail":"Sintel - YouTube"}]}}"""

        val decoded = (ServerLineClassifier().classify(line) as ServerLine.State).state

        assertNull(decoded.features)
        assertEquals("Sintel - YouTube", decoded.defaultDevice.sessions!!.single().detail)
    }

    @Test
    fun `outgoing session updates never carry a detail`() {
        val received = state(Samples.stateWithDetails).defaultDevice.sessions!!.last()

        val json = gson.toJson(Updates.session("dev", received.id, 20.0f, false))

        assertFalse(json.contains("detail"))
        assertEquals(
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"dev","sessions":[{"id":"${received.id}","volume":20.0,"muted":false}]}}""",
            json
        )
    }

    @Test
    fun `a captured detail-only update is recognised`() {
        val first = state(Samples.stateWithDetails)
        val next = state(Samples.stateWithDetailsNext)

        assertEquals("Sintel - YouTube", next.defaultDevice.sessions!!.last().detail)
        assertTrue(SessionDetails.onlyDetailsChanged(first, next))
    }

    @Test
    fun `details appearing or disappearing are detail-only changes`() {
        val withDetails = state(Samples.stateWithDetails)
        val without = withDetails.copy(
            defaultDevice = withDetails.defaultDevice.copy(
                sessions = withDetails.defaultDevice.sessions!!.map { it.copy(detail = null) }
            )
        )

        assertTrue(SessionDetails.onlyDetailsChanged(withDetails, without))
        assertTrue(SessionDetails.onlyDetailsChanged(without, withDetails))
    }

    @Test
    fun `other changes are not detail-only`() {
        val first = state(Samples.stateWithDetails)
        val sessions = first.defaultDevice.sessions!!
        fun withSessions(changed: List<AudioSession>) =
            first.copy(defaultDevice = first.defaultDevice.copy(sessions = changed))

        val volume = withSessions(sessions.mapIndexed { i, s -> if (i == 5) s.copy(detail = "Other", volume = 1f) else s })
        val icon = withSessions(sessions.mapIndexed { i, s -> if (i == 0) s.copy(iconId = "other") else s })
        val removed = withSessions(sessions.dropLast(1))
        val master = first.copy(defaultDevice = first.defaultDevice.copy(masterVolume = 3f))

        for (next in listOf(volume, icon, removed, master)) {
            assertFalse(SessionDetails.onlyDetailsChanged(first, next))
        }
    }

    @Test
    fun `identical or first states are not detail-only changes`() {
        val first = state(Samples.stateWithDetails)

        assertFalse(SessionDetails.onlyDetailsChanged(first, state(Samples.stateWithDetails)))
        assertFalse(SessionDetails.onlyDetailsChanged(null, first))
    }

    @Test
    fun `display detail keeps a normal detail as it is`() {
        val hebrew = "\u05E9\u05DC\u05D5\u05DD \u05E2\u05D5\u05DC\u05DD"
        val emoji = "Music \uD83C\uDFB5\u200D\uD83D\uDD25"

        assertEquals("Big Buck Bunny - YouTube", SessionDetails.displayDetail("Firefox", "Big Buck Bunny - YouTube"))
        assertEquals(hebrew, SessionDetails.displayDetail("Firefox", hebrew))
        assertEquals(emoji, SessionDetails.displayDetail("Firefox", emoji))
    }

    @Test
    fun `display detail hides nothing worth showing`() {
        assertNull(SessionDetails.displayDetail("Spotify", null))
        assertNull(SessionDetails.displayDetail("Spotify", ""))
        assertNull(SessionDetails.displayDetail("Spotify", " \u200B "))
        assertNull(SessionDetails.displayDetail("Spotify", "spotify"))
    }

    @Test
    fun `display detail cleans a non-conforming detail to one line`() {
        assertEquals("a b c", SessionDetails.displayDetail("x", "  a\n\tb\r\n c  "))
        assertEquals("abc", SessionDetails.displayDetail("x", "a\u0007b\u202Ec\u2066"))
        assertEquals("soft", SessionDetails.displayDetail("x", "so\u00ADft"))
    }

    @Test
    fun `display detail is capped at 120 code points with an ellipsis`() {
        val note = "\uD83C\uDFB5"

        val shown = SessionDetails.displayDetail("x", note.repeat(200))!!

        assertEquals(SessionDetails.MAX_CODE_POINTS, shown.codePointCount(0, shown.length))
        assertEquals(note.repeat(119) + "\u2026", shown)
        assertEquals("y".repeat(120), SessionDetails.displayDetail("x", "y".repeat(120)))
    }

    private fun ch(code: Int) = Char(code).toString()

    @Test
    fun `display detail turns any whitespace into single spaces and drops controls`() {
        assertEquals("Song Artist", SessionDetails.displayDetail("x", "Song" + ch(0x2028) + "Artist"))
        assertEquals("a b", SessionDetails.displayDetail("x", "a" + ch(0x2029) + ch(0x00A0) + ch(0x3000) + "b"))
        assertEquals("ab", SessionDetails.displayDetail("x", "a" + ch(0x1F) + "b"))
    }

    @Test
    fun `a detail equal to the name after cleaning is hidden`() {
        assertNull(SessionDetails.displayDetail("Google  Chrome", "google chrome"))
        assertNull(SessionDetails.displayDetail("Goo" + ch(0x200D) + "gle", "Google"))
        assertNull(SessionDetails.displayDetail("Firefox", "Fire" + ch(0x200B) + "fox"))
    }

    @Test
    fun `joiners stay only between visible characters`() {
        val zwj = ch(0x200D)
        val emoji = ch(0xD83D) + ch(0xDC69) + zwj + ch(0xD83D) + ch(0xDCBB)

        assertEquals(emoji, SessionDetails.displayDetail("x", emoji))
        assertEquals("a b", SessionDetails.displayDetail("x", zwj + "a " + zwj + "b" + zwj))
    }

    @Test
    fun `a cut never leaves a joiner before the ellipsis`() {
        val zwj = ch(0x200D)
        val detail = "y".repeat(118) + zwj + "z".repeat(10)

        val shown = SessionDetails.displayDetail("x", detail)!!

        assertEquals("y".repeat(118) + ch(0x2026), shown)
    }

    @Test
    fun `master updates carry only the master fields`() {
        assertEquals(
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"dev","masterVolume":40.0,"masterMuted":true}}""",
            gson.toJson(Updates.master("dev", 40.0f, true))
        )
    }

    @Test
    fun `invisible characters are removed but flag tags are kept`() {
        val tag = { cp: Int -> String(Character.toChars(cp)) }
        val england = tag(0x1F3F4) + tag(0xE0067) + tag(0xE0062) + tag(0xE0065) + tag(0xE006E) + tag(0xE0067) + tag(0xE007F)

        assertEquals("ab", SessionDetails.displayDetail("x", "a" + ch(0x061C) + ch(0x180E) + "b"))
        assertEquals("ab", SessionDetails.displayDetail("x", "a" + ch(0x034F) + ch(0x3164) + "b"))
        assertNull(SessionDetails.displayDetail("x", ch(0x3164) + ch(0x3164)))
        assertEquals("Match $england", SessionDetails.displayDetail("x", "Match $england"))
    }

    @Test
    fun `a first character longer than the cap falls back to a code point cut`() {
        val accent = ch(0x0301)

        val shown = SessionDetails.displayDetail("x", "a" + accent.repeat(200) + "bcd")!!

        assertEquals("a" + accent.repeat(118) + ch(0x2026), shown)
    }

    @Test
    fun `a cut keeps a flag whole`() {
        val flag = String(Character.toChars(0x1F1EB)) + String(Character.toChars(0x1F1F7))

        val shown = SessionDetails.displayDetail("x", "y".repeat(118) + flag + "z".repeat(10))!!

        assertEquals("y".repeat(118) + ch(0x2026), shown)
    }

    @Test
    fun `device updates carry only the device id`() {
        assertEquals(
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"dev"}}""",
            gson.toJson(Updates.device("dev"))
        )
    }

    @Test
    fun `spoken label is the name then the detail`() {
        assertEquals("Firefox, Big Buck Bunny - YouTube", SessionDetails.spokenLabel("Firefox", "Big Buck Bunny - YouTube"))
        assertEquals("Sintel", SessionDetails.spokenLabel(null, "Sintel"))
    }

    @Test
    fun `no detail leaves the name to be read as it is`() {
        assertNull(SessionDetails.spokenLabel("Spotify", null))
        assertNull(SessionDetails.spokenLabel("Spotify", ""))
        assertNull(SessionDetails.spokenLabel("Spotify", "  "))
        assertNull(SessionDetails.spokenLabel("Spotify", "spotify"))
    }
}
