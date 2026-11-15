package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** A line received from the server, classified by which top-level key it carries. */
sealed class ServerLine {
    data class State(val state: PcAudio) : ServerLine()
    data class Icon(val reply: IconReply) : ServerLine()

    /** Valid JSON with neither `defaultDevice` nor `icon`; ignored for forward compatibility. */
    object Unknown : ServerLine()

    data class Malformed(val reason: String) : ServerLine()
}

class ServerLineClassifier(private val gson: Gson = Gson()) {

    fun classify(line: String): ServerLine {
        val obj: JsonObject = try {
            val element = JsonParser.parseString(line)
            if (!element.isJsonObject) return ServerLine.Malformed("not a JSON object")
            element.asJsonObject
        } catch (e: JsonParseException) {
            return ServerLine.Malformed("invalid JSON: ${e.message}")
        }

        return try {
            when {
                obj.get("defaultDevice")?.isJsonObject == true ->
                    ServerLine.State(gson.fromJson(obj, PcAudio::class.java))
                obj.has("icon") -> {
                    val message = gson.fromJson(obj, IconReplyMessage::class.java)
                    IconReply.from(message.icon)?.let { ServerLine.Icon(it) }
                        ?: ServerLine.Malformed("invalid icon reply")
                }
                else -> ServerLine.Unknown
            }
        } catch (e: JsonParseException) {
            ServerLine.Malformed("unexpected field type: ${e.message}")
        }
    }
}
