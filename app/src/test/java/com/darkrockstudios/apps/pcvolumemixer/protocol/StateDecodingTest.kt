package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StateDecodingTest {
    private val gson = Gson()

    @Test
    fun `state decodes icon ids and opaque session ids`() {
        val state = gson.fromJson(Samples.state, PcAudio::class.java)

        assertEquals(8, state.protocolVersion)
        assertEquals("Speakers (Mock Audio)", state.defaultDevice.name)
        val sessions = state.defaultDevice.sessions!!
        assertEquals(
            listOf(
                "mock-9ff8bf89-firefox", "mock-9ff8bf89-spotify", "mock-9ff8bf89-discord",
                "mock-9ff8bf89-musique", "mock-9ff8bf89-firefox-radio", "mock-9ff8bf89-firefox-video"
            ),
            sessions.map { it.id }
        )
        assertFalse(sessions.any { it.id.contains(state.defaultDevice.deviceId) })
        assertEquals("demo1b64cd9", sessions[1].iconId)
        assertEquals(65.0f, sessions[1].volume)
    }

    @Test
    fun `state from a server without icons has no icon ids`() {
        val state = gson.fromJson(Samples.stateNoIcons, PcAudio::class.java)

        assertTrue(state.defaultDevice.sessions!!.all { it.iconId == null })
    }

    @Test
    fun `session with only the required members decodes`() {
        val state = gson.fromJson(
            """{"protocolVersion":8,"defaultDevice":{"deviceId":"d","sessions":[{"name":"A","id":"a","volume":1.0,"muted":true}]}}""",
            PcAudio::class.java
        )

        val session = state.defaultDevice.sessions!!.single()
        assertNull(session.iconId)
        assertNull(session.detail)
        assertTrue(session.muted)
    }

    @Test
    fun `session updates use the default device id and send the session id unchanged`() {
        val state = gson.fromJson(Samples.state, PcAudio::class.java)
        val session = state.defaultDevice.sessions!!.first()

        val json = gson.toJson(Updates.session(state.defaultDevice.deviceId, session.id, 29.0f, true))

        assertEquals(
            """{"protocolVersion":8,"defaultDevice":{"deviceId":"0f4090a9-dee2-4563-ba29-0ad6b93d9e22",""" +
                """"sessions":[{"id":"mock-9ff8bf89-firefox","volume":29.0,"muted":true}]}}""",
            json
        )
    }
}
