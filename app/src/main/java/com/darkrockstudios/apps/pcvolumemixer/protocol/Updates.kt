package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import com.darkrockstudios.apps.pcvolumemixer.data.AudioDevice
import com.darkrockstudios.apps.pcvolumemixer.data.AudioSession
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio

/** Volume and mute messages the app sends; display members such as `detail` are never sent. */
object Updates {
    fun device(deviceId: String): PcAudio =
        PcAudio(PROTOCOL_VERSION, null, null, AudioDevice(deviceId, null, null, null, null))

    fun master(deviceId: String, volume: Float, muted: Boolean): PcAudio =
        PcAudio(PROTOCOL_VERSION, null, null, AudioDevice(deviceId, null, volume, muted, null))

    fun session(deviceId: String, sessionId: String, volume: Float, muted: Boolean): PcAudio =
        PcAudio(
            PROTOCOL_VERSION,
            null,
            null,
            AudioDevice(deviceId, null, null, null, listOf(AudioSession(null, sessionId, volume, muted)))
        )
}
