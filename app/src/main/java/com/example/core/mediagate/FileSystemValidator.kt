package com.example.core.mediagate

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

interface FileSystemValidator {
    suspend fun isAccessible(uriString: String): Boolean
}

class DefaultFileSystemValidator(
    private val context: Context
) : FileSystemValidator {
    override suspend fun isAccessible(uriString: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse(uriString)
            if (uri.scheme == "file") {
                val path = uri.path ?: return@withContext false
                val file = File(path)
                file.exists() && file.canRead()
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
            }
        } catch (e: Exception) {
            false
        }
    }
}
