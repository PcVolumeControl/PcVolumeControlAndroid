package com.darkrockstudios.apps.pcvolumemixer.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IconRequestPlannerTest {
    private val cached = HashSet<String>()
    private val planner = IconRequestPlanner(isAvailable = { it in cached })

    private fun ids(count: Int, prefix: String = "id") = (0 until count).map { "$prefix$it" }

    @Test
    fun `requests unknown ids once, deduplicated, in session order`() {
        assertEquals(listOf(listOf("b", "a")), planner.onState(listOf("b", "a", "b", null, "a")))
        assertEquals(emptyList<List<String>>(), planner.onState(listOf("a", "b")))
        assertEquals(setOf("a", "b"), planner.outstandingIds)
    }

    @Test
    fun `cached and invalid ids are not requested`() {
        cached += "a"

        assertEquals(listOf(listOf("c")), planner.onState(listOf("a", "Bad-Id", "", "x".repeat(65), "c")))
    }

    @Test
    fun `batches to at most 32 ids per request`() {
        val requests = planner.onState(ids(40))

        assertEquals(listOf(32, 8), requests.map { it.size })
        assertEquals(ids(40), requests.flatten())
    }

    @Test
    fun `never more than 64 outstanding`() {
        val first = planner.onState(ids(100))

        assertEquals(listOf(32, 32), first.map { it.size })
        assertEquals(64, planner.outstandingIds.size)
        assertEquals(emptyList<List<String>>(), planner.onState(ids(10, "more")))
        assertEquals(64, planner.outstandingIds.size)
    }

    @Test
    fun `queued ids are sent once a full request fits`() {
        planner.onState(ids(100))

        for (i in 0 until 31) {
            assertEquals(emptyList<List<String>>(), planner.onReply("id$i", found = true))
        }
        val next = planner.onReply("id31", found = true)

        assertEquals(listOf((64 until 96).map { "id$it" }), next)
        assertEquals(64, planner.outstandingIds.size)
    }

    @Test
    fun `the remainder is sent when it all fits`() {
        planner.onState(ids(70))

        for (i in 0 until 5) planner.onReply("id$i", found = true)
        val next = planner.onReply("id5", found = true)

        assertEquals(listOf((64 until 70).map { "id$it" }), next)
        assertTrue(planner.outstandingIds.size <= 64)
    }

    @Test
    fun `queued ids cached in the meantime are skipped`() {
        planner.onState(ids(65))
        cached += "id64"

        for (i in 0 until 64) planner.onReply("id$i", found = true)

        assertTrue(planner.outstandingIds.isEmpty())
    }

    @Test
    fun `not found ids are not requested again on the same connection`() {
        planner.onState(listOf("gone"))
        planner.onReply("gone", found = false)

        assertEquals(emptyList<List<String>>(), planner.onState(listOf("gone")))
        assertEquals(emptyList<List<String>>(), planner.onState(listOf("gone")))
    }

    @Test
    fun `found ids are not requested again once cached`() {
        planner.onState(listOf("a"))
        cached += "a"
        planner.onReply("a", found = true)

        assertEquals(emptyList<List<String>>(), planner.onState(listOf("a")))
    }

    @Test
    fun `tracks which ids are outstanding or known`() {
        planner.onState(ids(66))
        planner.onReply("id0", found = false)

        assertTrue(planner.isOutstanding("id1"))
        assertTrue(!planner.isOutstanding("id0"))
        assertTrue(planner.isKnown("id0"))
        assertTrue(planner.isKnown("id64"))
        assertTrue(!planner.isOutstanding("id64"))
        assertTrue(!planner.isKnown("other"))
    }

    @Test
    fun `replies for ids that were never requested are ignored`() {
        assertEquals(emptyList<List<String>>(), planner.onReply("stranger", found = false))
        assertEquals(listOf(listOf("stranger")), planner.onState(listOf("stranger")))
    }

    @Test
    fun `reset on disconnect forgets outstanding and not found ids`() {
        planner.onState(ids(100))
        planner.onReply("id0", found = false)

        planner.reset()

        assertTrue(planner.outstandingIds.isEmpty())
        assertEquals(listOf(listOf("id0", "id1")), planner.onState(listOf("id0", "id1")))
    }

    @Test
    fun `after reconnect only ids still unknown are requested`() {
        planner.onState(listOf("a", "b", "c"))
        cached += "a"
        planner.onReply("a", found = true)

        planner.reset()

        assertEquals(listOf(listOf("b", "c")), planner.onState(listOf("a", "b", "c")))
    }
}
