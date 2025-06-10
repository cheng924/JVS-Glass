package com.example.jvsglass.ui.notification

data class NotificationModel(
    val packageName: String,
    val appName: String = "",
    val sender: String,
    val messages: MutableList<Pair<Long, String>>,  // 时间戳和消息内容
    var unreadCount: Int = 1,
    val notificationKey: String  // 通知唯一标识
)
