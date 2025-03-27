package com.example.jvsglass.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.classic.ClassicConstants.CONNECT_TIMEOUT_MS
import com.example.jvsglass.classic.ClassicConstants.BT_UUID
import com.example.jvsglass.utils.LogUtils
import java.io.File
import java.io.IOException

class ClassicRfcommClient(
    private val adapter: BluetoothAdapter,
    private val core: BluetoothConnectionCore,
    private val callback: BluetoothCallback
) {
    private var clientSocket: BluetoothSocket? = null
    private var reconnectAttempts = 0
    private val lock = Any()

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        LogUtils.info("[BluetoothClient] 开始设备搜索...")
        if (adapter.isDiscovering) {
            LogUtils.warn("[BluetoothClient] 发现已有搜索正在进行，取消前次搜索...")
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        LogUtils.info("[BluetoothClient] 尝试连接设备: ${device.name} (${device.address})")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            LogUtils.warn("[BluetoothClient] 设备未配对，尝试创建配对...")
            device.createBond() // 发起配对请求
        }
        core.setConnectionState(BluetoothConnectionCore.ConnectionState.CONNECTING)
        synchronized(lock) {
            reconnectAttempts = 0
        }
        ConnectThread(device).start()
    }

    fun sendMessage(message: String) = clientSocket?.let { core.sendMessage(message, it) }

    fun sendVoiceMessage(filePath: String) {
        clientSocket?.let { bluetoothSocket ->
            val file = File(filePath)
            val bytes = file.readBytes()
            core.sendVoiceMessage(bytes, bluetoothSocket)
        }
    }

    fun disconnect() {
        synchronized(lock) {
            clientSocket?.let { core.disconnect(it) }
            core.shutdown() // 关闭线程池
            clientSocket = null
        }
    }

    fun isConnected(): Boolean = core.isSocketConnected(clientSocket)

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val mmSocket: BluetoothSocket? by lazy(LazyThreadSafetyMode.NONE) {
            device.createRfcommSocketToServiceRecord(BT_UUID)
        }

        override fun run() {
            LogUtils.info("[ConnectThread] 正在建立连接线程... 设备: ${device.name} (${device.address})")
            adapter.cancelDiscovery()
            mmSocket?.let { socket ->
                val timeoutHandler = Handler(Looper.getMainLooper())
                val timeoutRunnable = Runnable {
                    LogUtils.error("[ConnectThread] 连接超时")
                    callback.onConnectionFailed("连接超时")
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        LogUtils.error("[BluetoothClient] 无法关闭socket: ${e.message}")
                    }
                    synchronized(lock) {
                        core.reconnect(callback, { attempt ->
                            reconnectAttempts = attempt
                            connectToDevice(device)
                        }, reconnectAttempts)
                    }
                }
                timeoutHandler.postDelayed(timeoutRunnable, CONNECT_TIMEOUT_MS)

                try {
                    LogUtils.info("[ConnectThread] 开始连接...")
                    socket.connect()
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    synchronized(lock) {
                        this@ClassicRfcommClient.clientSocket = socket
                        core.setConnectionState(BluetoothConnectionCore.ConnectionState.CONNECTED)
                    }
                    LogUtils.info("[ConnectThread] 连接成功: ${device.name}")
                    callback.onConnectionSuccess(device.name ?: "Unknown Device")
                    core.manageConnectedSocket(socket)
                } catch (e: IOException) {
                    timeoutHandler.removeCallbacks(timeoutRunnable)
                    LogUtils.error("[ConnectThread] 连接失败: ${e.stackTraceToString()}")
                    callback.onConnectionFailed("连接失败: ${e.message}")
                    try {
                        socket.close()
                    } catch (e: IOException) {
                        LogUtils.error("[ConnectThread] 关闭socket失败: ${e.message}")
                    }
                    synchronized(lock) {
                        core.reconnect(callback, { attempt ->
                            reconnectAttempts = attempt
                            connectToDevice(device)
                        }, reconnectAttempts)
                    }
                }
            } ?: LogUtils.error("[ConnectThread] 创建BluetoothSocket失败")
        }
    }
}