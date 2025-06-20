package com.example.jvsglass.ui.notification

import android.content.Context
import com.tencent.mmkv.MMKV

object NotificationRepository {
    val notifications = mutableListOf<NotificationModel>()

    fun addNotification(context: Context, packageName: String, sender: String, message: String, timestamp: Long, notificationKey: String) {
        val mmkv = MMKV.defaultMMKV()
        val enabledPackages = mmkv.decodeStringSet("enabled_packages") ?: emptySet()

        if (enabledPackages.contains(packageName)) {
            val appName = try {
                val pm = context.packageManager
                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
            } catch (e: Exception) {
                "未知应用"
            }

            val existing = notifications.find {
                it.packageName == packageName && it.sender == sender
            }
            if (existing != null) {
                existing.messages.add(0, Pair(timestamp, message))
                existing.unreadCount += 1
            } else {
                val model = NotificationModel(
                    packageName = packageName,
                    appName = appName,
                    sender = sender,
                    messages = mutableListOf(Pair(timestamp, message)),
                    unreadCount = 1,
                    notificationKey = notificationKey
                )
                notifications.add(0, model)
            }
        }
    }
}
