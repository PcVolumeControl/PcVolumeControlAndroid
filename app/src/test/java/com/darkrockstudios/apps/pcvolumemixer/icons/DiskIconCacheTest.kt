package com.darkrockstudios.apps.pcvolumemixer.icons

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DiskIconCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val dir by lazy { File(temp.root, "icons") }

    @Test
    fun `file names come from the id and size`() {
        assertEquals("demo1d94c5a_84.png", DiskIconCache.fileName("demo1d94c5a", 84))
        assertEquals("a_16.png", DiskIconCache.fileName("a", 16))
        assertEquals("${"f".repeat(64)}_256.png", DiskIconCache.fileName("f".repeat(64), 256))
    }

    @Test
    fun `ids that are not protocol ids never become file names`() {
        for (id in listOf("", "../x", "a/b", "..", "A", "a.png", "a_1", "x".repeat(65), "a\u0000")) {
            assertNull(id, DiskIconCache.fileName(id, 64))
        }
        assertNull(DiskIconCache.fileName("a", 15))
        assertNull(DiskIconCache.fileName("a", 257))
    }

    @Test
    fun `round trips bytes`() {
        val cache = DiskIconCache(dir, 1024)
        val bytes = byteArrayOf(1, 2, 3)

        assertFalse(cache.contains("abc", 64))
        assertTrue(cache.write("abc", 64, bytes))

        assertTrue(cache.contains("abc", 64))
        assertFalse(cache.contains("abc", 32))
        assertArrayEquals(bytes, cache.read("abc", 64))
        assertTrue(File(dir, "abc_64.png").isFile)
        assertEquals(listOf("abc_64.png"), dir.list()!!.toList())
    }

    @Test
    fun `invalid ids are neither written nor read`() {
        val cache = DiskIconCache(dir, 1024)

        assertFalse(cache.write("../evil", 64, byteArrayOf(1)))
        assertNull(cache.read("../evil", 64))
        assertFalse(File(temp.root, "evil_64.png").exists())
    }

    @Test
    fun `evicts least recently used files beyond the byte limit`() {
        val cache = DiskIconCache(dir, 250)
        cache.write("a", 64, ByteArray(100))
        File(dir, "a_64.png").setLastModified(1_000_000)
        cache.write("b", 64, ByteArray(100))
        File(dir, "b_64.png").setLastModified(2_000_000)
        cache.read("a", 64)

        cache.write("c", 64, ByteArray(100))

        assertTrue(cache.contains("a", 64))
        assertFalse(cache.contains("b", 64))
        assertTrue(cache.contains("c", 64))
    }

    @Test
    fun `an entry larger than the whole cache is not written`() {
        val cache = DiskIconCache(dir, 10)

        assertFalse(cache.write("a", 64, ByteArray(11)))
        assertFalse(cache.contains("a", 64))
    }

    @Test
    fun `delete removes the entry`() {
        val cache = DiskIconCache(dir, 1024)
        cache.write("a", 64, byteArrayOf(1))

        cache.delete("a", 64)

        assertFalse(cache.contains("a", 64))
    }
}
