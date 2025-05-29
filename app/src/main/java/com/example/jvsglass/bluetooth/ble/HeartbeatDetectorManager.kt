package com.example.jvsglass.bluetooth.ble

import android.Manifest
import android.os.Handler
import android.os.HandlerThread
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConstants.MAX_RETRY
import com.example.jvsglass.bluetooth.BluetoothConstants.HEART_INTERVAL
import org.greenrobot.eventbus.EventBus

object HeartbeatDetectorManager {
    private lateinit var bleClient: BLEGattClient
    private var retryCount = 0
    private lateinit var handler: Handler

    // 状态变更事件
    data class ConnectionEvent(val isConnected: Boolean)

    fun initialize(client: BLEGattClient) {
        this.bleClient = client
        val handlerThread = HandlerThread("HeartbeatThread").apply { start() }
        handler = Handler(handlerThread.looper)
    }

    fun startMonitoring() {
        handler.post(monitoringRunnable)
    }

    fun stopMonitoring() {
        handler.removeCallbacks(monitoringRunnable)
    }

    private val monitoringRunnable = object : Runnable {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun run() {
            checkConnectionState()
            handler.postDelayed(this, HEART_INTERVAL)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun checkConnectionState() {
        val currentState = bleClient.isConnected()
//        LogUtils.info("[Heartbeat] Posting event: isConnected=$currentState")
        EventBus.getDefault().postSticky(ConnectionEvent(currentState))

        // 断连处理
        if (!currentState) {
            handleDisconnection()
        } else {
            retryCount = 0 // 重置计数器
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDisconnection() {
        if (bleClient.isConnecting) return

        when {
            retryCount < MAX_RETRY -> {
                retryCount++
                bleClient.reconnect()
            }
            else -> {
                EventBus.getDefault().post(ConnectionEvent(false))
                stopMonitoring()
            }
        }
    }
}