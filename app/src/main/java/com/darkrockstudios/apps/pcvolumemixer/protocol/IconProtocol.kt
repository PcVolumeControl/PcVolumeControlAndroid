package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import java.util.Base64

/** Constants and helpers for the protocol v8 icon extension. */
object IconProtocol {
    const val FEATURE_ICONS = "icons"

    const val MAX_IDS_PER_REQUEST = 32
    const val MAX_OUTSTANDING = 64
    const val MIN_SIZE = 16
    const val MAX_SIZE = 256

    private val ID_PATTERN = Regex("[a-z0-9]{1,64}")

    /** Icon ids are 1 to 64 characters of `[a-z0-9]`, which also makes them safe in file names. */
    fun isValidId(id: String?): Boolean = id != null && ID_PATTERN.matches(id)

    fun supportsIcons(state: PcAudio): Boolean = state.features?.contains(FEATURE_ICONS) == true

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
    data class Image(override val id: String, val size: Int, val mime: String?, val data: String) : IconReply() {
        fun decodePng(): ByteArray? =
            try {
                Base64.getDecoder().decode(data)
            } catch (e: IllegalArgumentException) {
                null
            }
    }

    data class Missing(override val id: String, val error: String) : IconReply()

    companion object {
        /** Returns null when the reply has no valid id or is neither an image nor an error. */
        fun from(body: IconReplyBody?): IconReply? {
            val id = body?.id
            if (!IconProtocol.isValidId(id) || id == null) return null
            return when {
                body.error != null -> Missing(id, body.error)
                body.data != null && body.size != null -> Image(id, body.size, body.mime, body.data)
                else -> null
            }
        }
    }
}
