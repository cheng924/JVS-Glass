package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "conversation_id")
    val conversationId: String,

    @ColumnInfo(name = "message_content")
    val message: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: Long,

    @ColumnInfo(name = "is_sent")
    val isSent: Boolean, // true-发送消息，false-接收消息

    @ColumnInfo(name = "message_type")
    val type: Int, // 0-文本，1-语音，2-图片，3-文件，4-视频

    @ColumnInfo(name = "file_path")
    val path: String = "" // 文件存储路径
)
