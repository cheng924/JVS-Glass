package com.example.jvsglass.network

data class AiRequest(
    val message: String,
    val timestamp: String,
    val attachments: List<String> = emptyList()       // 语音、图片、文件、视频的路径
)
