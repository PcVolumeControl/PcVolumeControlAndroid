package com.darkrockstudios.apps.pcvolumemixer.icons

import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import java.io.File
import java.io.IOException

/**
 * Encoded icons on disk, keyed by icon id and requested size. Ids are content-derived, so entries
 * never go stale; the directory is kept under [maxBytes] by deleting the least recently used files.
 * Not thread safe: use it from a single thread.
 */
class DiskIconCache(private val dir: File, private val maxBytes: Long) {

    fun read(id: String, size: Int): ByteArray? {
        val file = fileFor(id, size) ?: return null
        return try {
            val bytes = file.readBytes()
            file.setLastModified(System.currentTimeMillis())
            bytes
        } catch (e: IOException) {
            null
        }
    }

    fun contains(id: String, size: Int): Boolean = fileFor(id, size)?.isFile == true

    fun write(id: String, size: Int, bytes: ByteArray): Boolean {
        val file = fileFor(id, size) ?: return false
        if (bytes.size > maxBytes) return false
        val temp = File(dir, file.name + TEMP_SUFFIX)
        return try {
            dir.mkdirs()
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                temp.delete()
                return false
            }
            trim()
            true
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    fun delete(id: String, size: Int) {
        fileFor(id, size)?.delete()
    }

    private fun trim() {
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        for (file in files.sortedBy { it.lastModified() }) {
            val length = file.length()
            if (file.delete()) total -= length
            if (total <= maxBytes) break
        }
    }

    private fun fileFor(id: String, size: Int): File? = fileName(id, size)?.let { File(dir, it) }

    companion object {
        private const val TEMP_SUFFIX = ".tmp"

        /** The file name for an entry, or null when [id] or [size] could not have come from the protocol. */
        fun fileName(id: String, size: Int): String? {
            if (!IconProtocol.isValidId(id) || size !in IconProtocol.MIN_SIZE..IconProtocol.MAX_SIZE) return null
            return "${id}_$size.png"
        }
    }
}
