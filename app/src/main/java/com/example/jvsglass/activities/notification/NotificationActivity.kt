package com.example.jvsglass.activities.notification

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

class NotificationActivity : AppCompatActivity() {
    private lateinit var adapter: NotificationAdapter

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            adapter.updateData(NotificationRepository.notifications
                .sortedByDescending { it.messages.maxOfOrNull { msg -> msg.first } })
        }
    }

    @SuppressLint("NotifyDataSetChanged", "MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        adapter = NotificationAdapter(NotificationRepository.notifications) { notification ->
            notification.unreadCount = 0
            adapter.notifyDataSetChanged()

            val messagesList = notification.messages.map { "${it.first}:${it.second}" }
            val appName = packageManager.getApplicationLabel(
                packageManager.getApplicationInfo(notification.packageName, 0)
            ).toString()
            val intent = Intent(this, NotificationDetailActivity::class.java).apply {
                putExtra("packageName", notification.packageName)
                putExtra("sender", notification.sender)
                putStringArrayListExtra("messages", ArrayList(messagesList))
                putExtra("appName", appName)
                putExtra("notificationKey", notification.notificationKey)
            }
            startActivity(intent)
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<RecyclerView>(R.id.rv_notifications).apply {
            layoutManager = LinearLayoutManager(this@NotificationActivity)
            this.adapter = this@NotificationActivity.adapter
        }

        findViewById<Button>(R.id.btn_filter).setOnClickListener {
            startActivity(Intent(this, FilterSettingsActivity::class.java))
        }

        LocalBroadcastManager.getInstance(this).registerReceiver(
            receiver,
            IntentFilter("com.example.notifications.NOTIFICATION_LISTENER")
        )
    }

    override fun onResume() {
        super.onResume()
        NotificationRepository.notifications.removeAll { it.unreadCount == 0 }
        adapter.updateData(NotificationRepository.notifications)
    }

    override fun onDestroy() {
        super.onDestroy()
        LocalBroadcastManager.getInstance(this).unregisterReceiver(receiver)
    }
}