package com.example.jvsglass.network

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.FileInputStream

class ProgressRequestBody(
    private val file: File,
    private val mediaType: String,
    private val progressListener: (Float) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType? = mediaType.toMediaTypeOrNull()

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var uploaded = 0L
        FileInputStream(file).use { inputStream ->
            var read: Int
            while (inputStream.read(buffer).also { read = it } != -1) {
                uploaded += read
                sink.write(buffer, 0, read)
                updateProgress(uploaded.toFloat() / file.length())
            }
        }
    }

    private fun updateProgress(percent: Float) {
        progressListener(percent)
    }

    companion object {
        private const val DEFAULT_BUFFER_SIZE = 2048
    }
}