package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio

/** Helpers for the optional session `detail`, which is display text and never an identity. */
object SessionDetails {

    /**
     * True when [next] differs from [previous] only in session details, so rows can be updated in
     * place instead of rebuilt.
     */
    fun onlyDetailsChanged(previous: PcAudio?, next: PcAudio): Boolean =
        previous != null && previous != next && withoutDetails(previous) == withoutDetails(next)

    /**
     * What a screen reader says for a session's name: the name alone, or the name then the detail.
     * Returns null when there is no detail, so the name is read as it is.
     */
    fun spokenLabel(name: String?, detail: String?): String? {
        if (detail.isNullOrBlank()) return null
        return if (name.isNullOrBlank()) detail else "$name, $detail"
    }

    private fun withoutDetails(state: PcAudio): PcAudio =
        state.copy(
            defaultDevice = state.defaultDevice.copy(
                sessions = state.defaultDevice.sessions?.map { it.copy(detail = null) }
            )
        )
}
