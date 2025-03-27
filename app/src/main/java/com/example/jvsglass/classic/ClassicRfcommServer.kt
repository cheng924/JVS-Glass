package com.example.jvsglass.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import androidx.annotation.RequiresPermission
import com.example.jvsglass.classic.ClassicConstants.BT_UUID
import com.example.jvsglass.classic.ClassicConstants.SERVICE_NAME
import com.example.jvsglass.utils.LogUtils
import java.io.File
import java.io.IOException

class ClassicRfcommServer(
    private val adapter: BluetoothAdapter,
    private val core: BluetoothConnectionCore,
    private val callback: BluetoothCallback
) : Thread() {
    private var serverSocket: BluetoothServerSocket? = null
    private val connectedSockets = mutableListOf<BluetoothSocket>()
    private var isRunning = false
    private val lock = Any()
    private var reconnectAttempts = 0

    @SuppressLint("HardwareIds")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun run() {
        LogUtils.info("[BluetoothServer] 正在启动服务端线程...")
        try {
            serverSocket = adapter.listenUsingRfcommWithServiceRecord(SERVICE_NAME, BT_UUID)
            LogUtils.info(
                """
                [BluetoothServer] 服务端启动成功
                |- 本地蓝牙名称: ${adapter.name}
                |- 本地蓝牙地址: ${adapter.address}
                |- 服务名称: $SERVICE_NAME
                |- 服务UUID: $BT_UUID
                """.trimIndent()
            )

            isRunning = true
            callback.onConnectionSuccess("Server")

            while (isRunning) {
                LogUtils.info("[BluetoothServer] 等待客户端连接...")
                val socket = serverSocket?.accept()
                socket?.let {
                    synchronized(lock) {
                        if (it.isConnected) {
                            connectedSockets.add(it)
                            LogUtils.info("[BluetoothServer] 客户端已连接: ${it.remoteDevice.name}")
                            callback.onConnectionSuccess(it.remoteDevice.name ?: "Unknown Device")
                            core.setConnectionState(BluetoothConnectionCore.ConnectionState.CONNECTED)
                            core.manageConnectedSocket(it)
                        } else {
                            LogUtils.warn("[BluetoothServer] 接受的Socket未连接，丢弃")
                            it.close()
                        }
                    }
                }
            }
        } catch (e: IOException) {
            if (isRunning) {
                LogUtils.error("[BluetoothServer] 服务端致命错误: ${e.stackTraceToString()}")
                callback.onConnectionFailed("服务端致命错误: ${e.message}")
                synchronized(lock) {
                    core.reconnect(callback, { attempt ->
                        reconnectAttempts = attempt
                        start()
                    }, reconnectAttempts)
                }
            }
        } catch (e: SecurityException) {
            LogUtils.error("[BluetoothServer] 权限错误: ${e.stackTraceToString()}")
            callback.onConnectionFailed("权限错误: ${e.message}")
        }
    }

    fun sendMessage(message: String) {
        synchronized(lock) {
            connectedSockets.removeAll { !it.isConnected }
            connectedSockets.forEach { core.sendMessage(message, it) }
        }
    }

    fun sendVoiceMessage(filePath: String) {
        synchronized(lock) {
            connectedSockets.filter { it.isConnected }.forEach { socket ->
                val file = File(filePath)
                val bytes = file.readBytes()
                core.sendVoiceMessage(bytes, socket)
            }
        }
    }

    fun isConnected(): Boolean {
        synchronized(lock) {
            return connectedSockets.any { core.isSocketConnected(it) }
        }
    }

    fun cancel() {
        LogUtils.warn("[BluetoothServer] 正在取消服务端...")
        isRunning = false
        synchronized(lock) {
            connectedSockets.forEach { core.disconnect(it) }
            connectedSockets.clear()
            serverSocket?.close()
        }
        core.shutdown() // 关闭线程池
        LogUtils.info("[BluetoothServer] 服务端资源已释放")
    }
}