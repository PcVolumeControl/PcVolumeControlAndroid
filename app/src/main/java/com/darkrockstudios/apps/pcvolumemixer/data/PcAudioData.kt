package com.darkrockstudios.apps.pcvolumemixer.data

/**
 * Created by adamw on 9/16/2017.
 */
data class PcAudio(val protocolVersion: Int, val applicationVersion: String?, val deviceIds: MutableMap<String,String>?, val defaultDevice: AudioDevice)

data class AudioSession(val name: String?, val id: String, val volume: Float, val muted: Boolean)

data class AudioDevice(val deviceId: String,
                       val name: String?,
                       val masterVolume: Float?,
                       val masterMuted: Boolean?,
                       val sessions: List<AudioSession>?)