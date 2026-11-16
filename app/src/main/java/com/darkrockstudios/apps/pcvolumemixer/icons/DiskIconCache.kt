package com.darkrockstudios.apps.pcvolumemixer.icons

import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import java.io.File
import java.io.IOException

/**
 * Encoded icons on disk, keyed by icon id and requested size. Ids are content-derived, so entries
 * never go stale; the directory is kept under [maxBytes] by deleting the least recently used files,
 * judged by modification time. Not thread safe, and assumes nothing else writes to [dir].
 */
class DiskIconCache(private val dir: File, private val maxBytes: Long) {

    /** Bytes in [dir], computed on first use. */
    private var totalBytes = -1L

    fun read(id: String, size: Int): ByteArray? {
        val file = fileFor(id, size) ?: return null
        if (!file.isFile) return null
        return try {
            val bytes = file.readBytes()
            file.setLastModified(System.currentTimeMillis())
            bytes
        } catch (e: IOException) {
            null
        }
    }

    fun contains(id: String, size: Int): Boolean = fileFor(id, size)?.isFile == true

    /** Records a use of an entry so it is evicted after less recently used ones. */
    fun markUsed(id: String, size: Int) {
        fileFor(id, size)?.takeIf { it.isFile }?.setLastModified(System.currentTimeMillis())
    }

    fun write(id: String, size: Int, bytes: ByteArray): Boolean {
        val file = fileFor(id, size) ?: return false
        if (bytes.size > maxBytes) return false
        val total = currentTotal()
        val replaced = if (file.isFile) file.length() else 0L
        val temp = File(dir, file.name + TEMP_SUFFIX)
        return try {
            dir.mkdirs()
            temp.writeBytes(bytes)
            if (!temp.renameTo(file)) {
                temp.delete()
                return false
            }
            totalBytes = total - replaced + bytes.size
            if (totalBytes > maxBytes) trim()
            true
        } catch (e: IOException) {
            temp.delete()
            false
        }
    }

    fun delete(id: String, size: Int) {
        val file = fileFor(id, size) ?: return
        val length = file.length()
        if (file.delete() && totalBytes >= 0) totalBytes -= length
    }

    private fun currentTotal(): Long {
        if (totalBytes < 0) totalBytes = files().sumOf { it.length() }
        return totalBytes
    }

    private fun trim() {
        val files = files()
        var total = files.sumOf { it.length() }
        for (file in files.sortedBy { it.lastModified() }) {
            if (total <= maxBytes) break
            val length = file.length()
            if (file.delete()) total -= length
        }
        totalBytes = total
    }

    private fun files(): List<File> = dir.listFiles()?.filter { it.isFile } ?: emptyList()

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
