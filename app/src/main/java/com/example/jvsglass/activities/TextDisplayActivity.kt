package com.example.jvsglass.activities;

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.ble.BLEManager
import com.example.jvsglass.utils.ToastUtils

class TextDisplayActivity : AppCompatActivity() {
    private val bleClient by lazy { BLEManager.getClient(this) }

    @SuppressLint("MissingInflatedId")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_display)

        // 检查是否有保存的设备地址，并尝试自动连接
        val prefs = getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
        val lastDeviceAddress = prefs.getString("last_connected_device_address", null)
        if (lastDeviceAddress != null) {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
            bleClient.connectToDevice(device) // 触发自动连接
        }

        // 获取传递数据
        val fileName = intent.getStringExtra("filename") ?: ""
        val fileDate = intent.getStringExtra("filedate") ?: ""
        val fileContent = intent.getStringExtra("filecontent") ?: ""

        // 初始化视图
        findViewById<TextView>(R.id.tvTitle).text = fileName
        findViewById<TextView>(R.id.tvDate).text = fileDate
        findViewById<TextView>(R.id.tvContent).apply {
            text = fileContent
            movementMethod = ScrollingMovementMethod()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            ToastUtils.show(this, "设置文本")
        }

        findViewById<LinearLayout>(R.id.btnStart).setOnClickListener {
            ToastUtils.show(this, "开始")
            sendMessage(fileContent)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessage(fileContent: String) {
        if (fileContent.isEmpty()) return
        bleClient.sendMessage(fileContent)
    }
}