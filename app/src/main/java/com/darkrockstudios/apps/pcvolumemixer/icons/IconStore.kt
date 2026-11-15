package com.darkrockstudios.apps.pcvolumemixer.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.WorkerThread
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.roundToInt

/**
 * Decoded icons by id and requested size: a byte-bounded memory cache in front of [DiskIconCache].
 * [peek] may be called from any thread; everything else runs on [executor].
 */
class IconStore private constructor(cacheDir: File) {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "IconStore").apply { isDaemon = true }
    }

    private val disk = DiskIconCache(File(cacheDir, DISK_DIR), MAX_DISK_BYTES)

    private val memory = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    fun peek(id: String, size: Int): Bitmap? = memory.get(key(id, size))

    @WorkerThread
    fun isCached(id: String, size: Int): Boolean = peek(id, size) != null || disk.contains(id, size)

    /** Loads [id] from disk into memory if needed. Returns true when it was not in memory before. */
    @WorkerThread
    fun loadFromDisk(id: String, size: Int): Boolean {
        if (peek(id, size) != null) return false
        val bytes = disk.read(id, size) ?: return false
        val bitmap = decode(bytes, size)
        if (bitmap == null) {
            disk.delete(id, size)
            return false
        }
        memory.put(key(id, size), bitmap)
        return true
    }

    /** Decodes and caches a PNG from the server. Returns false when it is not a usable image. */
    @WorkerThread
    fun put(id: String, size: Int, png: ByteArray): Boolean {
        val bitmap = decode(png, size) ?: return false
        memory.put(key(id, size), bitmap)
        disk.write(id, size, png)
        return true
    }

    @WorkerThread
    fun markUsed(id: String, size: Int) = disk.markUsed(id, size)

    /**
     * Reads the header first so an image claiming huge dimensions is never allocated, and scales
     * anything larger than [size] down so the memory budget counts icons at the drawn size.
     */
    private fun decode(png: ByteArray, size: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, bounds)
        if (bounds.outWidth !in 1..IconProtocol.MAX_SIZE || bounds.outHeight !in 1..IconProtocol.MAX_SIZE) {
            return null
        }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size) ?: return null
        if (bitmap.width <= size && bitmap.height <= size) return bitmap

        val scale = size.toFloat() / maxOf(bitmap.width, bitmap.height)
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(bitmap, width, height, true)
        if (scaled !== bitmap) bitmap.recycle()
        return scaled
    }

    private fun key(id: String, size: Int) = "${id}_$size"

    companion object {
        private const val DISK_DIR = "icons"
        private const val MAX_MEMORY_BYTES = 4 * 1024 * 1024
        private const val MAX_DISK_BYTES = 4L * 1024 * 1024

        @Volatile
        private var instance: IconStore? = null

        fun get(context: Context): IconStore =
            instance ?: synchronized(this) {
                instance ?: IconStore(context.applicationContext.cacheDir).also { instance = it }
            }
    }
}
