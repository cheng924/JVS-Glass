package com.example.jvsglass

import android.Manifest
import android.app.Application
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.bluetooth.BLEClient
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.utils.VoiceManager
import com.tencent.mmkv.MMKV

class MyApp : Application() {
    private lateinit var voiceManager: VoiceManager

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    override fun onCreate() {
        super.onCreate()

        AppDatabaseProvider.init(this) // 数据库初始化
        MMKV.initialize(this)
        voiceManager = VoiceManager(this)
        BluetoothConnectManager.initialize(this, voiceManager)
        autoConnect()
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    private fun autoConnect() {
        Handler(Looper.getMainLooper()).postDelayed({
            val last = BLEClient.getInstance(this).getDeviceAddress()
            if (last != null) {
                val adapter = (getSystemService(Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager).adapter
                adapter.bondedDevices.firstOrNull { it.address == last }?.let {
                    BluetoothConnectManager.reconnectDevice(it)
                }
            }
        }, 2_000L)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT]
    )
    override fun onTerminate() {
        BluetoothConnectManager.disconnect()
        super.onTerminate()
    }
}