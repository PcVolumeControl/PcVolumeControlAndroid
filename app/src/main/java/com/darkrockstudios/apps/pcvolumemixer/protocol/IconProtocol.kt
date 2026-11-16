package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import java.util.Base64

/** Constants and helpers for the protocol v8 icon extension. */
object IconProtocol {
    const val MAX_IDS_PER_REQUEST = 32
    const val MAX_OUTSTANDING = 64
    const val MIN_SIZE = 16
    const val MAX_SIZE = 256

    private val ID_PATTERN = Regex("[a-z0-9]{1,64}")

    /** Icon ids are 1 to 64 characters of `[a-z0-9]`, which also makes them safe in file names. */
    fun isValidId(id: String?): Boolean = id != null && ID_PATTERN.matches(id)

    /** The size to request for an icon drawn at [pixels] physical pixels. */
    fun requestSize(pixels: Float): Int = Math.round(pixels).coerceIn(MIN_SIZE, MAX_SIZE)

    fun requestSize(dp: Float, density: Float): Int = requestSize(dp * density)
}

data class IconRequestMessage(
    val iconRequest: IconRequest,
    val protocolVersion: Int = PROTOCOL_VERSION
)

data class IconRequest(val ids: List<String>, val size: Int)

/** Wire form of an `icon` line. Every field is nullable because Gson does not enforce Kotlin nullability. */
data class IconReplyMessage(val protocolVersion: Int?, val icon: IconReplyBody?)

data class IconReplyBody(
    val id: String?,
    val size: Int?,
    val mime: String?,
    val data: String?,
    val error: String?
)

/** A validated icon reply. */
sealed class IconReply {
    abstract val id: String

    /** [data] is still base64; [decodePng] decodes it so callers can do that off the reader thread. */
    data class Image(override val id: String, val size: Int, val data: String) : IconReply() {
        /** Returns null unless [data] is valid base64 of something that starts like a PNG. */
        fun decodePng(): ByteArray? {
            val bytes = try {
                Base64.getDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                return null
            }
            if (bytes.size < PNG_SIGNATURE.size) return null
            for (i in PNG_SIGNATURE.indices) {
                if (bytes[i] != PNG_SIGNATURE[i]) return null
            }
            return bytes
        }
    }

    data class Missing(override val id: String, val error: String) : IconReply()

    companion object {
        const val MIME_PNG = "image/png"
        const val ERROR_INVALID = "invalidReply"

        private val PNG_SIGNATURE = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)

        /**
         * Returns null when the reply has no valid id. A reply with a valid id but no usable image
         * becomes [Missing], so the id still counts as answered.
         */
        fun from(body: IconReplyBody?): IconReply? {
            val id = body?.id
            if (id == null || !IconProtocol.isValidId(id)) return null
            val size = body.size
            val data = body.data
            return when {
                body.error != null -> Missing(id, body.error)
                body.mime == MIME_PNG && data != null && size != null && size in 1..IconProtocol.MAX_SIZE ->
                    Image(id, size, data)
                else -> Missing(id, ERROR_INVALID)
            }
        }
    }
}
