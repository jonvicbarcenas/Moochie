package com.cute.moochie.util

import android.content.ContentResolver
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

object FileHelper {
    
    @Throws(IOException::class)
    fun createTempFileFromUri(contentResolver: ContentResolver, uri: Uri, cacheDir: File): File {
        val inputStream = contentResolver.openInputStream(uri) ?: throw IOException("Failed to open input stream")
        val file = File(cacheDir, "temp_upload.jpg")
        
        FileOutputStream(file).use { outputStream ->
            inputStream.use { input ->
                input.copyTo(outputStream)
            }
        }
        
        return file
    }
} 