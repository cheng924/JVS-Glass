package com.example.jvsglass.bluetooth.classic

import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import com.example.jvsglass.utils.LogUtils
import java.io.ByteArrayOutputStream
import com.example.jvsglass.bluetooth.classic.ClassicConstants.MAX_RECONNECT_ATTEMPTS
import com.example.jvsglass.bluetooth.classic.ClassicConstants.RECONNECT_DELAY_MS
import java.io.IOException
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicReference

class BluetoothConnectionCore(val callback: BluetoothCallback) {

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    // 通用回调接口
    interface BluetoothCallback {
        fun onConnectionSuccess(deviceName: String)
        fun onConnectionFailed(message: String)
        fun onMessageReceived(message: String)
        fun onVoiceMessageReceived(voiceData: ByteArray)
        fun onDisconnected()
    }

    private var connectionState = AtomicReference(ConnectionState.DISCONNECTED)
    private val executor: ExecutorService = Executors.newCachedThreadPool()

    // 检查socket连接状态
    fun isSocketConnected(socket: BluetoothSocket?): Boolean {
        return socket?.isConnected == true && connectionState.get() == ConnectionState.CONNECTED
    }

    // 管理连接的Socket，处理接收消息
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    fun manageConnectedSocket(socket: BluetoothSocket) {
        executor.execute {
            val inputStream = socket.inputStream
            val buffer = ByteArray(ClassicConstants.RECEIVE_BUFFER_SIZE)
            val messageBuffer = ByteArrayOutputStream()
            var messageSize = 0
            val dataBuffer = ByteArrayOutputStream()

            try {
                while (connectionState.get() == ConnectionState.CONNECTED) {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead == -1) break

                    LogUtils.debug("[BluetoothConnectionCore] 接收到字节数: $bytesRead, 数据: ${buffer.copyOf(bytesRead).joinToString()}")

                    val data = buffer.copyOf(bytesRead)
                    messageBuffer.write(data)
                    val messageStr = messageBuffer.toString(Charsets.UTF_8)
                    LogUtils.debug("[BluetoothConnectionCore] 当前消息缓冲: $messageStr")
                    val newlineIndex = messageStr.indexOf('\n')
                    if (newlineIndex != -1 && messageSize == 0) {
                        val header = messageStr.substring(0, newlineIndex)
                        LogUtils.debug("[BluetoothConnectionCore] 解析头部: $header")

                        if (header.startsWith("VOICE:")) {
                            try {
                                messageSize = header.substring(6).toInt()
                                LogUtils.info("[BluetoothConnectionCore] 检测到语音消息头，大小: $messageSize")
                            } catch (e: NumberFormatException) {
                                LogUtils.error("[BluetoothConnectionCore] 语音消息大小解析失败: ${e.message}")
                                messageBuffer.reset()
                                dataBuffer.reset()
                                continue
                            }
                        } else {
                            LogUtils.warn("[BluetoothConnectionCore] 未知消息头部: $header，丢弃")
                            messageBuffer.reset()
                            dataBuffer.reset()
                            continue
                        }

                        val remainingBytes = messageBuffer.toByteArray().copyOfRange(newlineIndex + 1, messageBuffer.size())
                        dataBuffer.write(remainingBytes)
                        messageBuffer.reset()

                        while (dataBuffer.size() < messageSize && connectionState.get() == ConnectionState.CONNECTED) {
                            val additionalBytesRead = inputStream.read(buffer)
                            if (additionalBytesRead == -1) break
                            dataBuffer.write(buffer, 0, additionalBytesRead)
                        }

                        if (dataBuffer.size() >= messageSize) {
                            processMessage(messageSize, dataBuffer)
                            messageSize = 0
                        } else {
                            LogUtils.warn("[BluetoothConnectionCore] 数据不足，预期: $messageSize, 实际: ${dataBuffer.size()}")
                            messageBuffer.reset()
                            dataBuffer.reset()
                        }
                    } else if (messageSize > 0) {
                        dataBuffer.write(data)
                        while (dataBuffer.size() < messageSize && connectionState.get() == ConnectionState.CONNECTED) {
                            val additionalBytesRead = inputStream.read(buffer)
                            if (additionalBytesRead == -1) break
                            dataBuffer.write(buffer, 0, additionalBytesRead)
                        }

                        if (dataBuffer.size() >= messageSize) {
                            processMessage(messageSize, dataBuffer)
                            messageSize = 0
                        } else {
                            LogUtils.warn("[BluetoothConnectionCore] 数据不足，预期: $messageSize, 实际: ${dataBuffer.size()}")
                            messageBuffer.reset()
                            dataBuffer.reset()
                        }
                    }
                }
            } catch (e: IOException) {
                if (connectionState.get() == ConnectionState.CONNECTED) {
                    callback.onConnectionFailed("连接丢失: ${e.message}")
                    disconnect(socket)
                }
            }
        }
    }

    // 处理接收到的消息
    private fun processMessage(messageSize: Int, dataBuffer: ByteArrayOutputStream) {
        val receivedData = dataBuffer.toByteArray()
        val completeData = receivedData.copyOf(messageSize)
        LogUtils.info("[BluetoothConnectionCore] 收到完整语音数据，大小: ${completeData.size} 字节")
        callback.onVoiceMessageReceived(completeData)

        if (receivedData.size > messageSize) {
            val remaining = receivedData.copyOfRange(messageSize, receivedData.size)
            dataBuffer.reset()
            dataBuffer.write(remaining)
        } else {
            dataBuffer.reset()
        }
    }
    // 重连机制
    fun reconnect(callback: BluetoothCallback, connectAction: (Int) -> Unit, currentAttempt: Int) {
        if (currentAttempt < MAX_RECONNECT_ATTEMPTS) {
            LogUtils.info("[BluetoothConnectionCore] 尝试重连 (${currentAttempt + 1}/$MAX_RECONNECT_ATTEMPTS)")
            Handler(Looper.getMainLooper()).postDelayed({
                connectAction(currentAttempt + 1)
            }, RECONNECT_DELAY_MS)
        } else {
            LogUtils.error("[BluetoothConnectionCore] 重连失败，达到最大尝试次数")
            callback.onConnectionFailed("重连失败")
        }
    }

    // 断开连接
    fun disconnect(socket: BluetoothSocket) {
        LogUtils.info("[BluetoothConnectionCore] 正在断开连接...")
        try {
            if (connectionState.get() != ConnectionState.DISCONNECTED) {
                socket.close()
                setConnectionState(ConnectionState.DISCONNECTED)
                LogUtils.info("[BluetoothConnectionCore] 已成功断开连接")
                callback.onDisconnected()
            }
        } catch (e: IOException) {
            LogUtils.error("[BluetoothConnectionCore] 无法关闭Socket: ${e.message}")
        }
    }

    // 设置连接状态
    fun setConnectionState(state: ConnectionState) {
        connectionState.set(state)
        LogUtils.info("[BluetoothConnectionCore] 连接状态更新: $state")
    }

    // 关闭线程池
    fun shutdown() {
        executor.shutdown()
        LogUtils.info("[BluetoothConnectionCore] 线程池已关闭")
    }
}