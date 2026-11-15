package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import com.darkrockstudios.apps.pcvolumemixer.data.AudioDevice
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio

/** Messages the app sends to change the server's audio state. */
object Updates {
    /** Sets one session's volume and mute; display members such as `detail` are never sent. */
    fun session(deviceId: String, sessionId: String, volume: Float, muted: Boolean): PcAudio =
        PcAudio(
            PROTOCOL_VERSION,
            null,
            null,
            AudioDevice(deviceId, null, null, null, listOf(AudioSession(null, sessionId, volume, muted)))
        )
}
