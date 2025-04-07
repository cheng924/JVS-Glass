package com.example.jvsglass.utils

import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File

object FileUtils {
    // 将File转换为MultipartBody.Part（自动识别MIME类型）
    private fun createFilePart(file: File): MultipartBody.Part {
        val mimeType = when (file.extension) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            else -> "application/octet-stream"
        }
        val requestBody = file.asRequestBody(mimeType.toMediaTypeOrNull())
        return MultipartBody.Part.createFormData("files", file.name, requestBody)
    }

    // 批量转换多个文件
    fun createMultiParts(files: List<File>): List<MultipartBody.Part> {
        return files.map { createFilePart(it) }
    }
}