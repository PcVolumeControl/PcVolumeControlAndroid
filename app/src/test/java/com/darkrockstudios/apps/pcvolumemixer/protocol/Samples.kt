package com.darkrockstudios.apps.pcvolumemixer.protocol

/**
 * Wire lines captured from the reference server's mock backend, one JSON line per file. All are
 * version 8 except [stateV7], which the original Windows server's protocol produces.
 */
object Samples {
    fun load(name: String): String {
        val stream = Samples::class.java.getResourceAsStream("/samples/$name")
            ?: error("Missing sample $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trimEnd('\n', '\r')
    }

    /** Icon ids on every session and details on two of them. */
    val state get() = load("state.json")

    /** The state that followed [state], in which only one session's detail changed. */
    val stateNext get() = load("state_next.json")

    /** From a server that serves no icons: no icon ids. */
    val stateNoIcons get() = load("state_no_icons.json")

    /** From a server set not to send details. */
    val stateNoDetails get() = load("state_no_details.json")

    val stateV7 get() = load("state_v7.json")
    val iconImage get() = load("icon_image.json")
    val iconNotFound get() = load("icon_not_found.json")
}
