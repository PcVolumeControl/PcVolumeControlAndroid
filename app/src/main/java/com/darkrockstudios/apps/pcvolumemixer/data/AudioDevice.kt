package com.darkrockstudios.apps.pcvolumemixer.data

/**
 * Created by adamw on 9/16/2017.
 */
data class AudioDevice(val deviceId: String,
                       val name: String,
                       val masterVolume: Float?,
                       val masterMuted: Boolean?,
                       val sessions: List<AudioSession>)