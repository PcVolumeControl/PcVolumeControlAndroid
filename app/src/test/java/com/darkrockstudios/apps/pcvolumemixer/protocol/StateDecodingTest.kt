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

class StateDecodingTest {
    private val gson = Gson()

    @Test
    fun `v7 state decodes without features or icon ids`() {
        val state = gson.fromJson(Samples.stateV7, PcAudio::class.java)

        assertEquals(7, state.protocolVersion)
        assertNull(state.features)
        assertFalse(IconProtocol.supportsIcons(state))
        assertEquals("Speakers (Mock Audio)", state.defaultDevice.name)
        val sessions = state.defaultDevice.sessions!!
        assertEquals(4, sessions.size)
        assertTrue(sessions.all { it.iconId == null })
        assertEquals("Musique été", sessions[3].name)
    }

    @Test
    fun `v8 state decodes features and icon ids`() {
        val state = gson.fromJson(Samples.stateWithIcons, PcAudio::class.java)

        assertEquals(7, state.protocolVersion)
        assertEquals(listOf("icons"), state.features)
        assertTrue(IconProtocol.supportsIcons(state))
        val sessions = state.defaultDevice.sessions!!
        assertEquals(
            listOf("demo1d94c5a", "demo1b64cd9", "demo174d94c", "demo14cd968"),
            sessions.map { it.iconId }
        )
        assertEquals(65.0f, sessions[1].volume)
    }

    @Test
    fun `unknown features do not enable icons`() {
        val state = gson.fromJson(
            """{"protocolVersion":7,"features":["somethingElse"],"defaultDevice":{"deviceId":"d","sessions":[]}}""",
            PcAudio::class.java
        )

        assertEquals(listOf("somethingElse"), state.features)
        assertFalse(IconProtocol.supportsIcons(state))
    }

    @Test
    fun `session with partial icon fields keeps the others`() {
        val state = gson.fromJson(
            """{"protocolVersion":7,"features":["icons"],"defaultDevice":{"deviceId":"d","sessions":[{"name":"A","id":"a","volume":1.0,"muted":true}]}}""",
            PcAudio::class.java
        )

        val session = state.defaultDevice.sessions!!.single()
        assertNull(session.iconId)
        assertTrue(session.muted)
    }

    @Test
    fun `outgoing updates do not gain new keys`() {
        val update = PcAudio(
            7, null, null,
            AudioDevice("dev", null, null, null, listOf(AudioSession(null, "s", 20.0f, false)))
        )

        assertEquals(
            """{"protocolVersion":7,"defaultDevice":{"deviceId":"dev","sessions":[{"id":"s","volume":20.0,"muted":false}]}}""",
            gson.toJson(update)
        )
    }
}
