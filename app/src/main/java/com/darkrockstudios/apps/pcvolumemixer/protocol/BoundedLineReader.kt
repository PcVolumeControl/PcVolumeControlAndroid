package com.darkrockstudios.apps.pcvolumemixer.protocol

import java.io.IOException
import java.io.Reader

class LineTooLongException(maxChars: Int) : IOException("Line longer than $maxChars characters")

/**
 * Reads `\n` terminated lines, dropping a trailing `\r`, and fails instead of buffering a line
 * longer than [maxChars].
 */
class BoundedLineReader(private val reader: Reader, private val maxChars: Int = MAX_LINE_CHARS) {
    private val buffer = CharArray(BUFFER_CHARS)
    private var position = 0
    private var limit = 0

    /** Returns null at end of stream. A final line without a terminator is still returned. */
    fun readLine(): String? {
        val line = StringBuilder()
        while (true) {
            if (position == limit) {
                val read = reader.read(buffer)
                if (read < 0) return if (line.isEmpty()) null else line.toString()
                position = 0
                limit = read
            }

            var end = position
            while (end < limit && buffer[end] != '\n') end++

            if (line.length + (end - position) > maxChars + 1) throw LineTooLongException(maxChars)
            line.appendRange(buffer, position, end)

            if (end < limit) {
                position = end + 1
                if (line.isNotEmpty() && line[line.length - 1] == '\r') line.setLength(line.length - 1)
                if (line.length > maxChars) throw LineTooLongException(maxChars)
                return line.toString()
            }
            position = limit
        }
    }

    companion object {
        /** The protocol requires accepting at least 1 MiB; icon lines are at most about 700 KiB. */
        const val MAX_LINE_CHARS = 4 * 1024 * 1024
        private const val BUFFER_CHARS = 16 * 1024
    }
}
