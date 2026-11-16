package com.darkrockstudios.apps.pcvolumemixer.protocol

/**
 * Decides which icon ids to request on one connection, keeping within the protocol limits.
 * Not thread safe: use it from a single thread.
 *
 * @param isAvailable whether an id is already cached and so never needs requesting.
 */
class IconRequestPlanner(
    private val isAvailable: (String) -> Boolean,
    private val maxIdsPerRequest: Int = IconProtocol.MAX_IDS_PER_REQUEST,
    private val maxOutstanding: Int = IconProtocol.MAX_OUTSTANDING
) {
    private val outstanding = LinkedHashSet<String>()
    private val queued = LinkedHashSet<String>()
    private val notFound = HashSet<String>()

    val outstandingIds: Set<String> get() = outstanding

    fun isOutstanding(id: String): Boolean = id in outstanding

    /** Whether [id] is requested, waiting to be requested, or known to be missing on this connection. */
    fun isKnown(id: String): Boolean = id in outstanding || id in queued || id in notFound

    /** Returns the requests to send now, each a list of ids. */
    fun onState(iconIds: Iterable<String?>): List<List<String>> {
        for (id in iconIds) {
            if (id == null || !IconProtocol.isValidId(id)) continue
            if (isKnown(id)) continue
            if (isAvailable(id)) continue
            queued += id
        }
        return drain()
    }

    /** Records the reply for [id] and returns any requests that can now be sent. */
    fun onReply(id: String, found: Boolean): List<List<String>> {
        if (!outstanding.remove(id)) return emptyList()
        if (!found) notFound += id
        return drain()
    }

    /** Forgets everything about the connection, including not-found ids, which may be retried later. */
    fun reset() {
        outstanding.clear()
        queued.clear()
        notFound.clear()
    }

    /**
     * Sends queued ids when a full request fits, or all of the remainder does, so replies trickling
     * in do not each trigger a one-id request. Outstanding ids are released only by their reply:
     * the server answers every id exactly once, and re-requesting one it still holds could exceed
     * its outstanding limit, which closes the connection.
     */
    private fun drain(): List<List<String>> {
        queued.removeAll { isAvailable(it) }
        val free = maxOutstanding - outstanding.size
        if (queued.isEmpty() || free < minOf(queued.size, maxIdsPerRequest)) return emptyList()

        val ids = queued.take(free)
        queued.removeAll(ids.toSet())
        outstanding += ids
        return ids.chunked(maxIdsPerRequest)
    }
}
