package com.darkrockstudios.apps.pcvolumemixer.icons

import android.util.Log
import com.darkrockstudios.apps.pcvolumemixer.TcpClient
import com.darkrockstudios.apps.pcvolumemixer.data.PcAudio
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconReply
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconRequest
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconRequestMessage
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconRequestPlanner
import com.google.gson.Gson

/**
 * Icon loading for one connection. All work runs on the store's executor, which also serialises
 * access to the planner.
 *
 * @param onIconsChanged called on a background thread when new icons are in memory.
 */
class IconSession(
    val client: TcpClient,
    private val store: IconStore,
    private val sizePx: Int,
    private val gson: Gson,
    private val onIconsChanged: () -> Unit
) {
    private val planner = IconRequestPlanner(isAvailable = { store.isCached(it, sizePx) })

    fun onState(state: PcAudio) = submit {
        val ids = state.defaultDevice.sessions.orEmpty()
            .mapNotNull { it.iconId }
            .filter { IconProtocol.isValidId(it) }
            .distinct()

        var loaded = false
        for (id in ids) {
            if (store.loadFromDisk(id, sizePx)) loaded = true
        }
        if (loaded) onIconsChanged()

        send(planner.onState(IconProtocol.supportsIcons(state), ids))
    }

    fun onReply(reply: IconReply) = submit {
        val found = when (reply) {
            is IconReply.Image -> reply.decodePng()?.let { store.put(reply.id, sizePx, it) } == true
            is IconReply.Missing -> false
        }
        if (found) onIconsChanged()
        else Log.d(TAG, "No icon for ${reply.id}")

        send(planner.onReply(reply.id, found))
    }

    fun onDisconnect() = submit { planner.reset() }

    private fun send(batches: List<List<String>>) {
        for (ids in batches) {
            client.sendMessageAsync(gson.toJson(IconRequestMessage(IconRequest(ids, sizePx))))
        }
    }

    private fun submit(block: () -> Unit) {
        store.executor.execute {
            try {
                block()
            } catch (e: RuntimeException) {
                Log.e(TAG, "Icon work failed", e)
            }
        }
    }

    companion object {
        private val TAG = IconSession::class.java.simpleName
    }
}
