package com.example.jvsglass.activities.jvsai

data class AiMessage(
    val message: String,
    val timestamp: String,
    val isSent: Boolean,        // true表示发送的消息，false表示接收的消息
    val type: Int = TYPE_TEXT,  // 默认文本类型
    val duration: Int = 0,      // 语音消息时长，单位:秒
    val path: String = ""       // 语音、图片、文件、视频的路径
) {
    companion object {
        const val TYPE_TEXT = 0
        const val TYPE_VOICE = 1
        const val TYPE_IMAGE = 2
        const val TYPE_FILE = 3
        const val TYPE_VIDEO = 4
    }
}
