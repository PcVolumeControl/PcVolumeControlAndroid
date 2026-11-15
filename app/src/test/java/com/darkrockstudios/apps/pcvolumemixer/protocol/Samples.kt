package com.darkrockstudios.apps.pcvolumemixer.protocol

/** Wire lines captured from the server's mock backend, one JSON line per file. */
object Samples {
    fun load(name: String): String {
        val stream = Samples::class.java.getResourceAsStream("/samples/$name")
            ?: error("Missing sample $name")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }.trimEnd('\n', '\r')
    }

    val stateWithIcons get() = load("state_icons.json")
    val stateV7 get() = load("state_v7.json")
    val iconImage get() = load("icon_image.json")
    val iconNotFound get() = load("icon_not_found.json")
}
