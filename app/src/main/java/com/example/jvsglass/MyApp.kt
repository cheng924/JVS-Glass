package com.example.jvsglass

import android.Manifest
import android.app.Application
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BLEGattClient
import com.example.jvsglass.bluetooth.HeartbeatDetectorManager
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.utils.LogUtils

class MyApp : Application() {
    private val bleClient by lazy { BLEGattClient.getInstance(this) }

    override fun onCreate() {
        super.onCreate()

        AppDatabaseProvider.init(this) // 数据库初始化
        autoConnect()   // 自动连接蓝牙设备
        initHeartbeat() // 心跳监测初始化
    }

    private fun autoConnect() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (!bleClient.isConnected()) {
                LogUtils.info("[MyApp] 尝试自动连接...")
                bleClient.autoConnect()
            }
        }, 2_000L) // 延迟2秒执行
    }

    private fun initHeartbeat() {
        HeartbeatDetectorManager.initialize(bleClient)
        Handler(Looper.getMainLooper()).postDelayed({
            LogUtils.info("[MyApp] 心跳监测启动")
            HeartbeatDetectorManager.startMonitoring()
        }, 3_000L) // 3秒后启动监控
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onTerminate() {
        bleClient.disconnect()
        HeartbeatDetectorManager.stopMonitoring()
        super.onTerminate()
    }
}