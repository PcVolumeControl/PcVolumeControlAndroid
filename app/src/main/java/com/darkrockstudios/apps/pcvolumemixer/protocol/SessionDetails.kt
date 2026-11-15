package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio

/** Helpers for the optional session `detail`, which is display text and never an identity. */
object SessionDetails {
    const val MAX_CODE_POINTS = 120
    private const val ELLIPSIS = "\u2026"

    private val WHITESPACE = Regex("\\s+")

    /**
     * True when [next] differs from [previous] only in session details, so rows can be updated in
     * place instead of rebuilt.
     */
    fun onlyDetailsChanged(previous: PcAudio?, next: PcAudio): Boolean =
        previous != null && previous != next && withoutDetails(previous) == withoutDetails(next)

    /**
     * The detail to show for a session, or null when there is nothing worth showing. Servers already
     * clean details; this keeps a non-conforming one to a single short line without invisible
     * formatting, and hides a detail that only repeats the name.
     */
    fun displayDetail(name: String?, detail: String?): String? {
        if (detail == null) return null
        val cleaned = detail
            .filterNot { Character.isISOControl(it) && !it.isWhitespace() || isInvisibleFormatting(it) }
            .replace(WHITESPACE, " ")
            .trim()
        if (cleaned.isEmpty() || cleaned.equals(name?.trim(), ignoreCase = true)) return null
        return capCodePoints(cleaned)
    }

    /**
     * What a screen reader says for a session's name: the name then the shown detail, or null when
     * no detail is shown so the name is read as it is.
     */
    fun spokenLabel(name: String?, detail: String?): String? {
        val shown = displayDetail(name, detail) ?: return null
        return if (name.isNullOrBlank()) shown else "$name, $shown"
    }

    private fun isInvisibleFormatting(c: Char): Boolean {
        val code = c.code
        return code == 0x00AD || code == 0x200B || code == 0x200E || code == 0x200F || code == 0xFEFF ||
            code in 0x202A..0x202E || code in 0x2060..0x2069
    }

    private fun capCodePoints(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_CODE_POINTS) return text
        val end = text.offsetByCodePoints(0, MAX_CODE_POINTS - 1)
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }

    private fun withoutDetails(state: PcAudio): PcAudio =
        state.copy(
            defaultDevice = state.defaultDevice.copy(
                sessions = state.defaultDevice.sessions?.map { it.copy(detail = null) }
            )
        )
}
