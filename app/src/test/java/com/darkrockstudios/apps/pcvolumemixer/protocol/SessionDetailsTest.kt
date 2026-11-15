package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.AudioDevice
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
        assertEquals(listOf("Firefox", "Firefox", "Firefox"), sessions.filter { it.name == "Firefox" }.map { it.name })
    }

    @Test
    fun `states without details decode with none`() {
        assertTrue(state(Samples.stateV7).defaultDevice.sessions!!.all { it.detail == null })
        assertTrue(state(Samples.stateWithIcons).defaultDevice.sessions!!.all { it.detail == null })
    }

    @Test
    fun `details need no feature name`() {
        val decoded = state(Samples.stateWithDetails)

        assertEquals(listOf("icons"), decoded.features)
        assertTrue(ServerLineClassifier().classify(Samples.stateWithDetails) is ServerLine.State)
    }

    @Test
    fun `outgoing session updates never carry a detail`() {
        val received = state(Samples.stateWithDetails).defaultDevice.sessions!!.last()
        val update = PcAudio(
            7, null, null,
            AudioDevice("dev", null, null, null, listOf(AudioSession(null, received.id, 20.0f, false)))
        )

        val json = gson.toJson(update)

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
    fun `spoken label is the name then the detail`() {
        assertEquals("Firefox, Big Buck Bunny - YouTube", SessionDetails.spokenLabel("Firefox", "Big Buck Bunny - YouTube"))
        assertEquals("Sintel", SessionDetails.spokenLabel(null, "Sintel"))
    }

    @Test
    fun `no detail leaves the name to be read as it is`() {
        assertNull(SessionDetails.spokenLabel("Spotify", null))
        assertNull(SessionDetails.spokenLabel("Spotify", ""))
        assertNull(SessionDetails.spokenLabel("Spotify", "  "))
    }
}
