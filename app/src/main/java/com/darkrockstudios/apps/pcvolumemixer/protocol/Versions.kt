package com.darkrockstudios.apps.pcvolumemixer.protocol

import com.darkrockstudios.apps.pcvolumemixer.PROTOCOL_VERSION

/** Which side needs updating when a server speaks another protocol version. */
enum class OlderSide { SERVER, APP }

object Versions {
    /**
     * A lower or missing version is an older server, such as the original Windows server, which
     * speaks version 7. A higher version means this app is the older side.
     */
    fun olderSide(serverVersion: Int?): OlderSide =
        if (serverVersion != null && serverVersion > PROTOCOL_VERSION) OlderSide.APP else OlderSide.SERVER
}
