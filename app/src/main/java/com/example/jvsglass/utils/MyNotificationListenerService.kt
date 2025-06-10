package com.example.jvsglass.utils

import android.app.Notification
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.example.jvsglass.ui.notification.NotificationRepository

class MyNotificationListenerService : NotificationListenerService() {
    override fun onListenerConnected() {
        super.onListenerConnected()
        LogUtils.info("[NotificationListenerService] 已连接")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName
        if (packageName == "com.android.systemui") {
            LogUtils.info("[NotificationListenerService] 过滤systemui通知")
            return
        }

        val extras = sbn.notification.extras
        val sender = extras.getString(Notification.EXTRA_TITLE) ?: "未知发送者"
        val message = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: "无内容"
        val timestamp = sbn.postTime
        val notificationKey = sbn.key

        LogUtils.info(("""
            [NotificationListenerService] 
            pkgName: $packageName,
            sender: $sender,
            length: ${message.length},
            message: $message,
            timestamp: $timestamp,
            key: $notificationKey
        """).trim())

        NotificationRepository.addNotification(this, packageName, sender, message, timestamp, notificationKey)

        val intent = Intent("com.example.notifications.NOTIFICATION_LISTENER")
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent)
    }
}