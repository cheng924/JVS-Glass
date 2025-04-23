package com.example.jvsglass.activities.jvsai

import java.util.UUID

data class AiMessage(
    val message: String,
    val timestamp: Long,
    val isSent: Boolean,        // true表示发送的消息，false表示接收的消息
    val type: Int = TYPE_TEXT,  // 默认文本类型
    val path: String = "",      // 语音、图片、文件、视频的路径
    val id: String = UUID.randomUUID().toString(),
    var isTemp: Boolean = false // 临时消息标识
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_IMAGE = 1
        const val TYPE_FILE = 2
    }
}
