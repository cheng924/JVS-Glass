package com.example.jvsglass

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.activities.notification.NotificationActivity
import com.example.jvsglass.activities.ai.JVSAIActivity
import com.example.jvsglass.activities.teleprompter.TeleprompterActivity
import com.example.jvsglass.activities.translate.TranslateActivity
import com.example.jvsglass.bluetooth.BluetoothConnectActivity
import com.example.jvsglass.bluetooth.ble.HeartbeatDetectorManager
import com.example.jvsglass.bluetooth.classic.ClassicConstants
import com.example.jvsglass.bluetooth.dual.DualBluetoothActivity
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.MyNotificationListenerService
import com.example.jvsglass.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_BT = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        checkPermissions()

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_translate, getString(R.string.translate), TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_teleprompt, getString(R.string.teleprompter), TeleprompterActivity::class.java),
            FunctionItem(R.drawable.ic_ai, getString(R.string.ai_beta), JVSAIActivity::class.java),
            FunctionItem(R.drawable.ic_notification, "消息通知", NotificationActivity::class.java)
//            FunctionItem(R.drawable.ic_transcribe, getString(R.string.transcribe), TranscribeActivity::class.java),
//            FunctionItem(R.drawable.ic_dash_board, getString(R.string.dashboard), DashboardActivity::class.java),
//            FunctionItem(R.drawable.ic_quicknote, getString(R.string.quick_note), QuickNoteActivity::class.java)
        )

        val recyclerView: RecyclerView = findViewById(R.id.rv_functions)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = FunctionAdapter(functions)

        // 设置按钮点击
        findViewById<ImageView>(R.id.iv_settings).setOnClickListener {
//            ToastUtils.show(this, getString(R.string.development_tips))
//            return@setOnClickListener
//            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btn_bluetooth).setOnClickListener {
            ToastUtils.show(this, getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, BluetoothActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.ll_bluetooth_connect).setOnClickListener {
            startActivity(Intent(this, BluetoothConnectActivity::class.java))
//            startActivity(Intent(this, DualBluetoothActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.ll_silent_mode).setOnClickListener {
            ToastUtils.show(this, getString(R.string.development_tips))
            return@setOnClickListener
        }
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, ClassicConstants.REQUEST_LOCATION)
        }

        if (!isNotificationListenerEnabled()) {
            promptEnableNotificationListener()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkgName = packageName
        val flat = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains("$pkgName/${MyNotificationListenerService::class.java.name}")
    }

    private fun promptEnableNotificationListener() {
        ToastUtils.show(this, "请开启通知访问权限，以使用通知功能")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onConnectionEvent(event: HeartbeatDetectorManager.ConnectionEvent) {
        LogUtils.debug("[MainActivity] Event received: ${event.isConnected}")
        val statusText = if (event.isConnected) "已连接" else "未连接"
        findViewById<TextView>(R.id.tv_bluetooth_status).text = statusText

        val statusText2 = if (event.isConnected) "蓝牙已连接" else "蓝牙未连接"
        findViewById<TextView>(R.id.btn_bluetooth).text = statusText2
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BT) {
            if (resultCode == RESULT_OK) {
                val name = data?.getStringExtra("CONNECTED_DEVICE")
                ToastUtils.show(this, "蓝牙已连接: $name")
            } else {
                ToastUtils.show(this, "未连接蓝牙")
            }
        }
    }
}