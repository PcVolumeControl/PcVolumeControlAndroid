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
            listOf("Firefox", "Spotify", "Discord", "Musique été", "Firefox", "Firefox"),
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
        val hebrew = "שלום עולם"
        val emoji = "Music 🎵‍🔥"

        assertEquals("Big Buck Bunny - YouTube", SessionDetails.displayDetail("Firefox", "Big Buck Bunny - YouTube"))
        assertEquals(hebrew, SessionDetails.displayDetail("Firefox", hebrew))
        assertEquals(emoji, SessionDetails.displayDetail("Firefox", emoji))
    }

    @Test
    fun `display detail hides nothing worth showing`() {
        assertNull(SessionDetails.displayDetail("Spotify", null))
        assertNull(SessionDetails.displayDetail("Spotify", ""))
        assertNull(SessionDetails.displayDetail("Spotify", " ​ "))
        assertNull(SessionDetails.displayDetail("Spotify", "spotify"))
    }

    @Test
    fun `display detail cleans a non-conforming detail to one line`() {
        assertEquals("a b c", SessionDetails.displayDetail("x", "  a\n\tb\r\n c  "))
        assertEquals("abc", SessionDetails.displayDetail("x", "a\u0007b‮c⁦"))
        assertEquals("soft", SessionDetails.displayDetail("x", "so­ft"))
    }

    @Test
    fun `display detail is capped at 120 code points with an ellipsis`() {
        val note = "🎵"

        val shown = SessionDetails.displayDetail("x", note.repeat(200))!!

        assertEquals(SessionDetails.MAX_CODE_POINTS, shown.codePointCount(0, shown.length))
        assertEquals(note.repeat(119) + "…", shown)
        assertEquals("y".repeat(120), SessionDetails.displayDetail("x", "y".repeat(120)))
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
        assertNull(SessionDetails.spokenLabel("Spotify", "Spotify"))
    }
}
