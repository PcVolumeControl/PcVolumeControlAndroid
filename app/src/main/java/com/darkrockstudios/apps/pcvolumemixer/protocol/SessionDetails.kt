package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio

/** Helpers for the optional session `detail`, which is display text and never an identity. */
object SessionDetails {
    const val MAX_CODE_POINTS = 120
    private val ELLIPSIS = Char(0x2026).toString()
    private const val ZWNJ = 0x200C
    private const val ZWJ = 0x200D

    private val SPACES = Regex(" +")

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
        val cleaned = clean(detail)
        if (cleaned.isEmpty()) return null
        if (name != null && withoutJoiners(clean(name)).equals(withoutJoiners(cleaned), ignoreCase = true)) return null
        return capCodePoints(cleaned)
    }

    /** What a screen reader says for a session's name: the name then the shown detail, or null for the name alone. */
    fun spokenLabel(name: String?, detail: String?): String? {
        val shown = displayDetail(name, detail) ?: return null
        return if (name.isNullOrBlank()) shown else "$name, $shown"
    }

    /**
     * One line: whitespace of any kind becomes single spaces, other control and invisible
     * formatting characters are removed, and joiners stay only between two visible characters.
     */
    private fun clean(text: String): String {
        val mapped = StringBuilder(text.length)
        for (c in text) {
            when {
                isInvisibleFormatting(c.code) -> Unit
                isUnicodeWhitespace(c) -> mapped.append(' ')
                Character.isISOControl(c) -> Unit
                else -> mapped.append(c)
            }
        }
        return dropLooseJoiners(mapped.toString().replace(SPACES, " ").trim())
    }

    private fun dropLooseJoiners(text: String): String {
        val out = StringBuilder(text.length)
        for (i in text.indices) {
            val c = text[i]
            if (isJoiner(c)) {
                val before = out.lastOrNull()
                val after = text.getOrNull(i + 1)
                val between = before != null && before != ' ' && !isJoiner(before) &&
                    after != null && after != ' ' && !isJoiner(after)
                if (!between) continue
            }
            out.append(c)
        }
        return out.toString()
    }

    /** The Unicode White_Space property, which unlike Char.isWhitespace excludes U+001C to U+001F. */
    private fun isUnicodeWhitespace(c: Char): Boolean =
        Character.isSpaceChar(c) || c.code in 0x09..0x0D || c.code == 0x85

    private fun isJoiner(c: Char) = c.code == ZWNJ || c.code == ZWJ

    private fun withoutJoiners(text: String) = text.filterNot { isJoiner(it) }

    private fun isInvisibleFormatting(code: Int): Boolean =
        code == 0x00AD || code == 0x200B || code == 0x200E || code == 0x200F || code == 0xFEFF ||
            code in 0x202A..0x202E || code in 0x2060..0x2069

    private fun capCodePoints(text: String): String {
        if (text.codePointCount(0, text.length) <= MAX_CODE_POINTS) return text
        val end = text.offsetByCodePoints(0, MAX_CODE_POINTS - 1)
        return text.substring(0, end).trimEnd().trimEnd { isJoiner(it) } + ELLIPSIS
    }

    private fun withoutDetails(state: PcAudio): PcAudio =
        state.copy(
            defaultDevice = state.defaultDevice.copy(
                sessions = state.defaultDevice.sessions?.map { it.copy(detail = null) }
            )
        )
}
