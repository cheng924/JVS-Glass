package com.example.jvsglass.ui.bt

data class MessageItem(
    val text: String,              // 消息文本
    val voiceFilePath: String? = null // 语音文件路径，可为空
)
