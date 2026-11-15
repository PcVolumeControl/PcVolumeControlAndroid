package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser

/** A line received from the server, classified by which top-level key it carries. */
sealed class ServerLine {
    data class State(val state: PcAudio) : ServerLine()
    data class Icon(val reply: IconReply) : ServerLine()

    /** [version] is null when the line has no usable `protocolVersion`. */
    data class VersionMismatch(val version: Int?) : ServerLine()

    /** Valid JSON with neither `defaultDevice` nor `icon`; ignored for forward compatibility. */
    object Unknown : ServerLine()

    data class Malformed(val reason: String) : ServerLine()
}

class ServerLineClassifier(private val gson: Gson = Gson()) {

    private companion object {
        val OPTIONAL_SESSION_STRINGS = listOf("iconId", "detail")
    }

    fun classify(line: String): ServerLine {
        val obj: JsonObject = try {
            val element = JsonParser.parseString(line)
            if (!element.isJsonObject) return ServerLine.Malformed("not a JSON object")
            element.asJsonObject
        } catch (e: JsonParseException) {
            return ServerLine.Malformed("invalid JSON: ${e.message}")
        }

        val version = intOrNull(obj.get("protocolVersion"))
        if (version != PROTOCOL_VERSION) return ServerLine.VersionMismatch(version)

        return try {
            when {
                obj.has("defaultDevice") -> {
                    val problem = stateProblem(obj)
                    if (problem != null) {
                        ServerLine.Malformed(problem)
                    } else {
                        dropUnusableOptionalMembers(obj)
                        ServerLine.State(gson.fromJson(obj, PcAudio::class.java))
                    }
                }
                obj.has("icon") -> {
                    val message = gson.fromJson(obj, IconReplyMessage::class.java)
                    IconReply.from(message.icon)?.let { ServerLine.Icon(it) }
                        ?: ServerLine.Malformed("icon reply without a valid id")
                }
                else -> ServerLine.Unknown
            }
        } catch (e: JsonParseException) {
            ServerLine.Malformed("unexpected field type: ${e.message}")
        }
    }

    private fun intOrNull(element: JsonElement?): Int? {
        if (element == null || !element.isJsonPrimitive) return null
        return try {
            element.asInt
        } catch (e: NumberFormatException) {
            null
        }
    }

    /**
     * Gson ignores Kotlin nullability, so the fields the models declare non-null are checked here
     * rather than failing later on the UI thread.
     */
    private fun stateProblem(obj: JsonObject): String? {
        val device = obj.get("defaultDevice")
        if (device == null || !device.isJsonObject) return "defaultDevice is not an object"
        if (!isString(device.asJsonObject.get("deviceId"))) return "defaultDevice.deviceId is not a string"

        val sessions = device.asJsonObject.get("sessions")
        if (sessions != null && sessions.isJsonArray) {
            val badSession = sessions.asJsonArray.any { session ->
                !session.isJsonObject || !isString(session.asJsonObject.get("id"))
            }
            if (badSession) return "session without a string id"
        }

        val deviceIds = obj.get("deviceIds")
        if (deviceIds != null && deviceIds.isJsonObject) {
            if (deviceIds.asJsonObject.entrySet().any { !isString(it.value) }) return "deviceIds has a non-string name"
        }
        return null
    }

    /**
     * Optional display members that are not strings are removed rather than failing the whole
     * state, so one odd session cannot stop volume updates.
     */
    private fun dropUnusableOptionalMembers(obj: JsonObject) {
        val sessions = obj.getAsJsonObject("defaultDevice").get("sessions")
        if (sessions == null || !sessions.isJsonArray) return
        for (session in sessions.asJsonArray) {
            val members = session.asJsonObject
            for (name in OPTIONAL_SESSION_STRINGS) {
                val value = members.get(name) ?: continue
                if (!isString(value)) members.remove(name)
            }
        }
    }

    private fun isString(element: JsonElement?): Boolean =
        element != null && element.isJsonPrimitive && element.asJsonPrimitive.isString
}
