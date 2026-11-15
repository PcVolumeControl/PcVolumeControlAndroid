package com.darkrockstudios.apps.pcvolumemixer.icons

import android.graphics.Bitmap
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
 * access to the planner and the fields below. New icons are announced by [IconStore] listeners.
 */
class IconSession(
    val client: TcpClient,
    private val store: IconStore,
    private val sizePx: Int,
    private val gson: Gson
) {
    private val planner = IconRequestPlanner(isAvailable = { store.isCached(it, sizePx) })

    /** Ids already marked as used in the disk cache on this connection. */
    private val touched = HashSet<String>()

    @Volatile
    private var closed = false

    /** The cached icon for [id] at this session's size, from any thread. */
    fun cachedIcon(id: String): Bitmap? = store.peek(id, sizePx)

    fun onState(state: PcAudio) = submit {
        if (closed) return@submit
        val supported = IconProtocol.supportsIcons(state)
        if (!supported) return@submit
        val ids = state.defaultDevice.sessions.orEmpty()
            .mapNotNull { it.iconId }
            .filter { IconProtocol.isValidId(it) }
            .distinct()

        for (id in ids) {
            if (store.peek(id, sizePx) != null) {
                if (touched.add(id)) store.markUsed(id, sizePx)
            } else if (!planner.isKnown(id) && store.loadFromDisk(id, sizePx)) {
                touched += id
            }
        }

        send(planner.onState(supported, ids))
    }

    /** Replies that arrived before [close] are still cached, so their downloads are not wasted. */
    fun onReply(reply: IconReply) = submit {
        if (!planner.isOutstanding(reply.id)) {
            Log.d(TAG, "Ignoring unrequested icon ${reply.id}")
            return@submit
        }
        val found = when (reply) {
            is IconReply.Image -> reply.decodePng()?.let { store.put(reply.id, sizePx, it) } == true
            is IconReply.Missing -> false
        }
        if (found) {
            touched += reply.id
        } else {
            Log.d(TAG, "No icon for ${reply.id}")
        }

        send(planner.onReply(reply.id, found))
    }

    /**
     * Stops further icon requests; batches already being handed to the stopped client may still go
     * out and are dropped there. Replies already queued are still cached, then the planner is
     * reset. The session is not reused after its connection drops.
     */
    fun close() {
        closed = true
        store.executor.execute { planner.reset() }
    }

    private fun send(batches: List<List<String>>) {
        if (closed) return
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
