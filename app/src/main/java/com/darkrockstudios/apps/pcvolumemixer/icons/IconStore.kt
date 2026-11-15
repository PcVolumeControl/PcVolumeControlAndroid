package com.darkrockstudios.apps.pcvolumemixer.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.annotation.WorkerThread
import com.darkrockstudios.apps.pcvolumemixer.protocol.IconProtocol
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Decoded icons by id and requested size: a byte-bounded memory cache in front of [DiskIconCache].
 * [peek] and the listener methods may be called from any thread; everything else runs on
 * [executor]. Listeners are told, on [executor], whenever an icon enters memory.
 */
class IconStore private constructor(cacheDir: File) {

    val executor: ExecutorService = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "IconStore").apply { isDaemon = true }
    }

    private val disk = DiskIconCache(File(cacheDir, DISK_DIR), MAX_DISK_BYTES)

    private val memory = object : LruCache<String, Bitmap>(MAX_MEMORY_BYTES) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.allocationByteCount
    }

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    fun peek(id: String, size: Int): Bitmap? = memory.get(key(id, size))

    fun addListener(listener: () -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: () -> Unit) {
        listeners -= listener
    }

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
        addToMemory(id, size, bitmap)
        return true
    }

    /**
     * Decodes and caches a PNG from the server. Returns false when it is not a usable image. An
     * image larger than [size] is stored on disk at the scaled size.
     */
    @WorkerThread
    fun put(id: String, size: Int, png: ByteArray): Boolean {
        val bitmap = decode(png, size) ?: return false
        addToMemory(id, size, bitmap)
        disk.write(id, size, if (bitmap.width < bounds(png).first) encode(bitmap) ?: png else png)
        return true
    }

    private fun addToMemory(id: String, size: Int, bitmap: Bitmap) {
        memory.put(key(id, size), bitmap)
        for (listener in listeners) listener()
    }

    private fun bounds(png: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(png, 0, png.size, options)
        return Pair(options.outWidth, options.outHeight)
    }

    private fun encode(bitmap: Bitmap): ByteArray? {
        val out = ByteArrayOutputStream()
        return if (bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
    }

    @WorkerThread
    fun markUsed(id: String, size: Int) = disk.markUsed(id, size)

    /**
     * Reads the header first so an image claiming huge dimensions is never allocated, and scales
     * anything larger than [size] down so the memory budget counts icons at the drawn size: a
     * power-of-two subsampled decode, then one filtered scale of at most 2x.
     */
    private fun decode(png: ByteArray, size: Int): Bitmap? {
        val (width, height) = bounds(png)
        if (width !in 1..IconProtocol.MAX_SIZE || height !in 1..IconProtocol.MAX_SIZE) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = IconScaling.sampleSize(width, height, size)
        }
        val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size, options) ?: return null
        val (scaledWidth, scaledHeight) = IconScaling.fitWithin(bitmap.width, bitmap.height, size) ?: return bitmap

        val scaled = Bitmap.createScaledBitmap(bitmap, scaledWidth, scaledHeight, true)
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
