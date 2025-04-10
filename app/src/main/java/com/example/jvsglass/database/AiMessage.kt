package com.example.jvsglass.database

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.jvsglass.activities.jvsai.AiMessage

@Entity(tableName = "ai_messages")
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "message_content")
    val message: String,

    @ColumnInfo(name = "timestamp")
    val timestamp: String, // 格式："yyyy/MM/dd HH:mm:ss"

    @ColumnInfo(name = "is_sent")
    val isSent: Boolean, // true-发送消息，false-接收消息

    @ColumnInfo(name = "message_type")
    val type: Int, // 0-文本，1-语音，2-图片，3-文件，4-视频

    @ColumnInfo(name = "duration")
    val duration: Int = 0, // 语音消息时长（秒）

    @ColumnInfo(name = "file_path")
    val path: String = "" // 文件存储路径
)

// 实体与业务对象转换扩展函数
fun AiMessageEntity.toAiMessage() = AiMessage(
    message = this.message,
    timestamp = this.timestamp,
    isSent = this.isSent,
    type = this.type,
    path = this.path
)

fun AiMessage.fromAiMessage() = AiMessageEntity(
    message = this.message,
    timestamp = this.timestamp,
    isSent = this.isSent,
    type = this.type,
    path = this.path
)
