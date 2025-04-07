package com.example.jvsglass.network

import com.google.gson.annotations.SerializedName

data class UploadResult(
    @SerializedName("code") val code: Int,
    @SerializedName("message") val message: String,
    @SerializedName("fileUrls") val fileUrls: List<String>
)
