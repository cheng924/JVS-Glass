package com.example.jvsglass.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.BluetoothConstants.CONNECT_TIMEOUT
import com.example.jvsglass.bluetooth.BluetoothConstants.MAX_RETRY
import com.example.jvsglass.bluetooth.BluetoothConstants.RECEIVE_BUFFER_SIZE
import com.example.jvsglass.bluetooth.BluetoothConstants.RETRY_INTERVAL
import com.example.jvsglass.bluetooth.BluetoothConstants.UUID_RFCOMM
import com.example.jvsglass.utils.LogUtils
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

class ClassicRfcommClient(
    private val adapter: BluetoothAdapter,
    private val callback: BluetoothCallback
) {
    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED }

    interface BluetoothCallback {
        fun onConnectionSuccess(deviceName: String)
        fun onConnectionFailed(message: String)
        fun onVoiceMessageReceived(voiceData: ByteArray)
        fun onAudioStreamReceived(streamData: ByteArray)
        fun onDisconnected()
    }

    private var clientSocket: BluetoothSocket? = null
    private var coreState = AtomicReference(ConnectionState.DISCONNECTED)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private var reconnectAttempts = AtomicInteger(0)
    private val lock = Any()
    private var pendingAddress: String? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        LogUtils.info("[ClassicRfcommClient] 开始设备搜索...")
        if (adapter.isDiscovering) {
            LogUtils.warn("[ClassicRfcommClient] 发现已有搜索正在进行，取消前次搜索...")
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectToDevice(device: BluetoothDevice) {
        adapter.cancelDiscovery()
        LogUtils.info("[ClassicRfcommClient] 尝试连接设备: ${device.name} (${device.address})}")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            pendingAddress = device.address
            LogUtils.warn("[ClassicRfcommClient] 设备未配对，正在配对...")
            device.createBond() // 发起配对请求
            return
        }
        setState(ConnectionState.CONNECTING)
        reconnectAttempts.set(0)
        ConnectThread(device).start()
    }

    fun disconnect() {
        synchronized(lock) {
            clientSocket?.let { closeConnection(it) }
            executor.shutdown()
            clientSocket = null
        }
    }

    private fun setState(state: ConnectionState) {
        coreState.set(state)
        LogUtils.info("[ClassicRfcommClient] 状态: $state")
    }

    private fun closeConnection(socket: BluetoothSocket) {
        LogUtils.info("[ClassicRfcommClient] 断开连接...")
        try {
            socket.close()
            setState(ConnectionState.DISCONNECTED)
            LogUtils.info("[ClassicRfcommClient] 已断开连接")
            callback.onDisconnected()
        } catch (e: IOException) {
            LogUtils.error("[ClassicRfcommClient] 关闭Socket失败: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    private inner class ConnectThread(private val device: BluetoothDevice) : Thread() {
        private val socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(UUID_RFCOMM)

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun run() {
            LogUtils.info("[ClassicRfcommClient] 开始连接设备: ${device.name} (${device.address})")
            adapter.cancelDiscovery()

            val handler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                LogUtils.error("[ClassicRfcommClient] 手动超时，关闭 socket")
                try { socket?.close() } catch (_:IOException){}
                onConnectFailed("连接超时")
            }
            handler.postDelayed(timeoutRunnable, CONNECT_TIMEOUT)

            try {
                socket?.connect()
                handler.removeCallbacks(timeoutRunnable)
                LogUtils.info("[ClassicRfcommClient] 连接成功")
                onConnectSuccess(device)
            } catch (e:IOException) {
                handler.removeCallbacks(timeoutRunnable)
                LogUtils.error("[ClassicRfcommClient] 连接失败: ${e.message}")
                try { socket?.close() } catch (_:IOException){}
                onConnectFailed("连接失败: ${e.message}")
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun onConnectSuccess(device: BluetoothDevice) {
            synchronized(lock) {
                clientSocket = socket
                setState(ConnectionState.CONNECTED)
            }
            callback.onConnectionSuccess(device.name ?: "Unknown")
            manageSocket(socket!!)
            DualBluetoothManager.onDeviceConnected?.invoke(device)
        }

        private fun onConnectFailed(msg: String) {
            callback.onConnectionFailed(msg)
            scheduleReconnect(device)
        }
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    private fun scheduleReconnect(device: BluetoothDevice) {
        coreState.get().let {
            if (reconnectAttempts.get() < MAX_RETRY) {
                LogUtils.info("[ClassicRfcommClient] 重连 (${reconnectAttempts.get()+1}/$MAX_RETRY)")
                Handler(Looper.getMainLooper()).postDelayed({
                    reconnectAttempts.incrementAndGet()
                    connectToDevice(device)
                }, RETRY_INTERVAL)
            } else {
                LogUtils.error("[ClassicRfcommClient] 重连失败")
                callback.onConnectionFailed("重连失败")
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun manageSocket(socket: BluetoothSocket) {
        executor.execute {
            val inputStream = socket.inputStream
            val buffer = ByteArray(RECEIVE_BUFFER_SIZE)
            val dataBuffer = ByteArrayOutputStream()
            var messageSize = 0
            var isVoiceMessage = false

            try {
                while (coreState.get() == ConnectionState.CONNECTED) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead <= 0) break
                    LogUtils.debug("[ClassicRfcommClient] 接收字节数: $bytesRead")

                    dataBuffer.write(buffer, 0, bytesRead)
                    while (dataBuffer.size() > 0) {
                        if (messageSize == 0) {
                            val data = dataBuffer.toByteArray()
                            val headerEnd = findHeaderEnd(data)
                            if (headerEnd != -1) {
                                val header = String(data.copyOfRange(0, headerEnd), Charsets.UTF_8)
                                if (header.startsWith("VOICE:")) {
                                    messageSize = header.removePrefix("VOICE:").toIntOrNull() ?: 0
                                    isVoiceMessage = true
                                    dataBuffer.reset()
                                    dataBuffer.write(data.copyOfRange(headerEnd + 1, data.size))
                                } else if (header.startsWith("AUDIO_STREAM:")) {
                                    val lengthStr = header.removePrefix("AUDIO_STREAM:").toIntOrNull()
                                    if (lengthStr != null) {
                                        messageSize = lengthStr
                                        isVoiceMessage = false
                                        dataBuffer.reset()
                                        dataBuffer.write(data.copyOfRange(headerEnd + 1, data.size))
                                    } else {
                                        val payload = data.copyOfRange(headerEnd + 1, data.size)
                                        if (payload.isNotEmpty()) {
                                            callback.onAudioStreamReceived(payload)
                                        }
                                        dataBuffer.reset()
                                        break
                                    }
                                } else {
                                    LogUtils.warn("[ClassicRfcommClient] 未知头: $header，跳过一个字节")
                                    dataBuffer.reset()
                                    dataBuffer.write(data.copyOfRange(1, data.size))
                                }
                            } else {
                                LogUtils.debug("[ClassicRfcommClient] 等待完整消息头")
                                break
                            }
                        } else {
                            if (dataBuffer.size() >= messageSize) {
                                val completeData = dataBuffer.toByteArray().copyOf(messageSize)
                                if (isVoiceMessage) {
                                    callback.onVoiceMessageReceived(completeData)
                                } else {
                                    callback.onAudioStreamReceived(completeData)
                                }
                                val leftover = dataBuffer.toByteArray().copyOfRange(messageSize, dataBuffer.size())
                                dataBuffer.reset()
                                dataBuffer.write(leftover)
                                messageSize = 0
                                isVoiceMessage = false
                            } else {
                                LogUtils.debug("[ClassicRfcommClient] 等待完整数据: ${dataBuffer.size()}/$messageSize")
                                break
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                if (coreState.get() == ConnectionState.CONNECTED) {
                    callback.onConnectionFailed("连接丢失: ${e.message}")
                    closeConnection(socket)
                }
            }
        }
    }

    private fun findHeaderEnd(data: ByteArray): Int {
        for (i in data.indices) {
            if (data[i] == '\n'.code.toByte()) return i
        }
        return -1
    }
}