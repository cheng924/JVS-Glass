package com.example.jvsglass.activities.notification

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.MyNotificationListenerService
import java.util.Date

class NotificationDetailActivity : AppCompatActivity() {
    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification_detail)

        val packageName = intent.getStringExtra("packageName")
        val appName = "[消息来源：" + (intent.getStringExtra("appName") ?: "未知应用") + "]"
        val sender = intent.getStringExtra("sender") ?: "未知发送者"
        val messagesList = intent.getStringArrayListExtra("messages") ?: emptyList()
        val notificationKey = intent.getStringExtra("notificationKey")

        val detailText = messagesList.joinToString("\n") { str ->
            val parts = str.split(":", limit = 2)
            if (parts.size == 2) {
                val timestamp = parts[0].toLong()
                val message = getMessageStr(parts[1], sender)
                val timeStr = android.text.format.DateFormat.format("yyyy-MM-dd HH:mm:ss", Date(timestamp)).toString()
                "[$timeStr] $message\n"
            } else {
                "无效消息"
            }
        }

        findViewById<TextView>(R.id.tv_detail_title).text = sender
        findViewById<TextView>(R.id.tv_detail_pkg).text = appName
        findViewById<TextView>(R.id.tv_detail_text).text = detailText

        findViewById<TextView>(R.id.tv_jump_app).setOnClickListener {
            if (packageName != null) {
                openApp(packageName, this@NotificationDetailActivity)
            }
        }

        if (notificationKey != null) {
            try {
                val service = MyNotificationListenerService()
                service.cancelNotification(notificationKey)
            } catch (e: Exception) {
                LogUtils.error("[NotificationDetailActivity] Failed to cancel notification", e)
            }
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }
    }

    private fun getMessageStr(messageStr: String, sender: String): String {
        val message = if (messageStr.contains(sender)) {
            messageStr.substringAfter("$sender: ")
        } else {
            if (messageStr.contains(": ")) messageStr.substringAfter(": ") else messageStr
        }
        return message
    }

    private fun openApp(packageName: String, context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            } else {
                LogUtils.error("无法打开该应用")
            }
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtils.error("跳转失败：${e.message}")
        }
    }
}