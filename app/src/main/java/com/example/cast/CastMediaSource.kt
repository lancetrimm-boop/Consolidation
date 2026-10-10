package com.example.cast

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream
import java.io.RandomAccessFile
import java.nio.channels.FileChannel

sealed class CastMediaSource {
    abstract val length: Long
    abstract val mimeType: String
    abstract fun isSeekable(): Boolean
    abstract fun writeRange(start: Long, length: Long, outputStream: OutputStream)

    data class FileSource(val file: File, override val mimeType: String) : CastMediaSource() {
        override val length: Long get() = file.length()

        override fun isSeekable(): Boolean = file.exists() && file.canRead() && file.length() > 0L

        override fun writeRange(start: Long, length: Long, outputStream: OutputStream) {
            RandomAccessFile(file, "r").use { raf ->
                raf.seek(start)
                val buffer = ByteArray(64 * 1024)
                var remaining = length
                while (remaining > 0) {
                    val read = raf.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    if (read <= 0) break
                    outputStream.write(buffer, 0, read)
                    remaining -= read
                }
            }
        }
    }

    data class ContentResolverSource(
        val uri: Uri,
        val context: Context,
        override val mimeType: String
    ) : CastMediaSource() {
        private var cachedLength: Long = -1L

        override val length: Long
            get() {
                if (cachedLength >= 0) return cachedLength
                val len = try {
                    context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { pfd ->
                        pfd.length
                    } ?: -1L
                } catch (e: Exception) {
                    -1L
                }
                cachedLength = len
                return len
            }

        override fun isSeekable(): Boolean {
            if (length <= 0) return false
            var pfd: ParcelFileDescriptor? = null
            return try {
                pfd = context.contentResolver.openFileDescriptor(uri, "r")
                if (pfd == null) return false
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val channel = fis.channel
                    val size = channel.size()
                    channel.position(0)
                    size > 0
                }
            } catch (e: Exception) {
                Log.w("CastMediaSource", "Random access validation failed for URI $uri: ${e.message}")
                false
            } finally {
                try { pfd?.close() } catch (_: Exception) {}
            }
        }

        override fun writeRange(start: Long, length: Long, outputStream: OutputStream) {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: throw IOException("Failed to open FileDescriptor for $uri")
            try {
                FileInputStream(pfd.fileDescriptor).use { fis ->
                    val channel: FileChannel = fis.channel
                    channel.position(start)
                    val buffer = ByteArray(64 * 1024)
                    var remaining = length
                    while (remaining > 0) {
                        val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                        val read = fis.read(buffer, 0, toRead)
                        if (read <= 0) break
                        outputStream.write(buffer, 0, read)
                        remaining -= read
                    }
                }
            } finally {
                try { pfd.close() } catch (_: Exception) {}
            }
        }
    }
}
