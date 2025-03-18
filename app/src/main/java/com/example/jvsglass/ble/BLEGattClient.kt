package com.example.jvsglass.ble

import android.Manifest
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.ble.BLEConstants.CURRENT_MTU
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.PacketUtils
import com.example.jvsglass.utils.toHexString
import java.lang.ref.WeakReference
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue

class BLEGattClient(context: Context) {
    private var connectedDevice: BluetoothDevice? = null // 设备引用
    private val contextRef = WeakReference(context)
    private var bluetoothGatt: BluetoothGatt? = null
    private var isDisconnecting = false // 断联状态标记

    private val sendQueue = ConcurrentLinkedQueue<ByteArray>()
    private var isSending = false

    private val receivedPackets = mutableMapOf<Int, ByteArray>()
    private var expectedTotalPackets = -1
    private var currentReceivedCount = 0

    // 消息回调接口
    interface MessageListener {
        fun onMessageReceived(message: String)
        fun onMessageSent(message: String)
    }
    // 消息监听器
    var messageListener: MessageListener? = null

    // GATT客户端回调
    private val gattClientCallback = object : BluetoothGattCallback() {

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogUtils.info("[BLE] 已连接到设备 ${gatt.device.address}")
                        gatt.requestMtu(CURRENT_MTU)    // 连接成功时请求MTU
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogUtils.info("[BLE] 连接已断开")
                        disconnect() // 主动释放资源
                    }
                }
                else -> {
                    LogUtils.error("[BLE] 连接失败，错误码：$status")
                    disconnect() // 连接失败时立即清理资源
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogUtils.error("[BLE] 服务发现失败，状态码：$status")
                return
            }

            LogUtils.info("[BLE] 服务发现成功，发现服务数量：${gatt.services.size}")

            // 获取服务
            val service = gatt.getService(BLEConstants.SERVICE_UUID)
            if (service == null) {
                LogUtils.error("[BLE] 未找到服务 UUID=${BLEConstants.SERVICE_UUID}")
                return
            }

            // 获取特征值
            val characteristic = service.getCharacteristic(BLEConstants.CHARACTERISTIC_UUID)
            if (characteristic == null) {
                LogUtils.error("[BLE] 未找到特征值 UUID=${BLEConstants.CHARACTERISTIC_UUID}")
                return
            }

            // 启用通知
            val enableNotify = gatt.setCharacteristicNotification(characteristic, true)
            LogUtils.debug("[BLE] 启用通知结果：$enableNotify")

            val descriptor = characteristic.getDescriptor(BLEConstants.DESCRIPTOR_UUID)
            if (descriptor == null) {
                LogUtils.error("[BLE] 未找到描述符 UUID=${BLEConstants.DESCRIPTOR_UUID}")
                return
            }

            // 写入描述符值
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            val writeSuccess = gatt.writeDescriptor(descriptor)
            if (!writeSuccess) {
                LogUtils.error("[BLE] 无法写入描述符")
            } else {
                LogUtils.info("[BLE] 已请求启用通知")
            }
        }

        @Deprecated("Deprecated in Java")
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            Handler(Looper.getMainLooper()).post {
                try {
                    when (val result = PacketUtils.processPacket(value)) {
                        is PacketUtils.ProcessResult.Partial -> {
                            handlePartialPacket(result)
                        }
                        is PacketUtils.ProcessResult.Complete -> {
                            messageListener?.onMessageReceived(result.message)
                        }
                    }
                } catch (e: Exception) {
                    LogUtils.error("[BLE] 消息解析异常，数据：${value.toHexString()}", e)
                }
            }
        }

        // 分包处理数据
        private fun handlePartialPacket(result: PacketUtils.ProcessResult.Partial) {
            synchronized(receivedPackets) {
                // 初始化或重置
                if (expectedTotalPackets != result.total) {
                    receivedPackets.clear()
                    expectedTotalPackets = result.total
                    currentReceivedCount = 0
                }

                // 存储当前包
                if (!receivedPackets.containsKey(result.index)) {
                    receivedPackets[result.index] = result.payload
                    currentReceivedCount++
                }

                // 检查是否收集完成
                if (currentReceivedCount == expectedTotalPackets) {
                    // 组合数据
                    val fullData = ByteArray(receivedPackets.values.sumOf { it.size })
                    var offset = 0
                    for (i in 0 until expectedTotalPackets) {
                        val part = receivedPackets[i] ?: break
                        System.arraycopy(part, 0, fullData, offset, part.size)
                        offset += part.size
                    }

                    // 处理完整数据
                    val message = String(fullData, StandardCharsets.UTF_8)
                    LogUtils.info("[BLE] 收到完整消息：$message")

                    messageListener?.onMessageReceived(message)

                    // 重置状态
                    receivedPackets.clear()
                    expectedTotalPackets = -1
                    currentReceivedCount = 0
                }
            }
        }

        // MTU变化处理
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                CURRENT_MTU = mtu
                LogUtils.info("[BLE] MTU更新为：$mtu")
                // MTU 协商成功后发起服务发现
                val success = gatt.discoverServices()
                LogUtils.debug("[BLE] 服务发现请求结果：$success")
                if (!success) {
                    LogUtils.error("[BLE] 服务发现请求失败")
                }
            } else {
                LogUtils.error("[BLE] MTU更新失败，状态码：$status")
            }
        }

        // 写入确认回调
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                processNextPacket() // 继续发送下一个包
            } else {
                LogUtils.error("[BLE] 数据包发送失败，状态码：$status")
                isSending = false
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        disconnect() // 先关闭旧连接
        LogUtils.info("[BLE] 尝试连接设备 ${device.address}")
        connectedDevice = device // 保存设备对象
        val ctx = contextRef.get() ?: return
        bluetoothGatt = device.connectGatt(ctx, true, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        // 在分包前立即触发完整消息的回调
        messageListener?.onMessageSent(message)

        val packets = PacketUtils.createPackets(message, CURRENT_MTU)
        sendQueue.addAll(packets)

        if (!isSending) {
            processNextPacket()
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect() {
        if (isDisconnecting) return // 避免重复调用

        bluetoothGatt?.let { gatt ->
            isDisconnecting = true
            try {
                gatt.disconnect()
                Handler(Looper.getMainLooper()).postDelayed({
                    gatt.close()
                    bluetoothGatt = null
                    isDisconnecting = false
                    sendQueue.clear() // 清空发送队列
                    isSending = false // 重置发送状态
                    LogUtils.debug("[BLE] 资源完全释放完成")
                }, 500) // 增加延迟确保异步操作完成
                LogUtils.info("[BLE] 连接已主动断开")
            } catch (e: Exception) {
                // 处理权限被拒绝的情况
                LogUtils.error("[BLE] 断开异常: ${e.javaClass.simpleName}")
            }
        }
        connectedDevice = null // 清除设备引用
    }

    // 分包发送逻辑
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun processNextPacket() {
        val context = contextRef.get() ?: run {
            LogUtils.error("[BLE] 上下文已释放")
            sendQueue.clear()
            isSending = false
            return
        }

        if (sendQueue.isEmpty()) {
            LogUtils.debug("[BLE] 发送队列已清空")
            isSending = false
            return
        }

        val gatt = bluetoothGatt ?: run {
            LogUtils.error("[BLE] GATT连接不可用")
            sendQueue.clear()
            isSending = false
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = connectedDevice ?: run {
            LogUtils.error("[BLE] 设备引用丢失")
            sendQueue.clear()
            isSending = false
            return
        }

        when (bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)) {
            BluetoothProfile.STATE_CONNECTED -> {
                LogUtils.debug("[BLE] 设备连接正常 (${device.address})")
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                LogUtils.error("[BLE] 设备已断开连接")
                sendQueue.clear()
                isSending = false
                return
            }
            else -> {
                LogUtils.error("[BLE] 设备连接状态异常")
                sendQueue.clear()
                isSending = false
                return
            }
        }

        val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: run {
            LogUtils.error("[BLE] 服务不可用")
            sendQueue.clear()
            return
        }

        val characteristic = service.getCharacteristic(BLEConstants.CHARACTERISTIC_UUID) ?: run {
            LogUtils.error("[BLE] 特征值不可用")
            sendQueue.clear()
            return
        }

        // 检查特征值属性
        when {
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 -> {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0 -> {
                characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            }
            else -> {
                LogUtils.error("[BLE] 特征值不支持写入")
                sendQueue.clear()
                return
            }
        }

        val packet = sendQueue.poll() ?: run {
            isSending = false
            return
        }

        LogUtils.debug("[BLE] 发送数据包：${packet.toHexString()}")


        try {
            isSending = true
            characteristic.value = packet

            Handler(Looper.getMainLooper()).post {
                if (!gatt.writeCharacteristic(characteristic)) {
                    LogUtils.error("[BLE] 写入特征值失败")
                    LogUtils.debug("[BLE] 特征值UUID: ${characteristic.uuid}")
                    LogUtils.debug("[BLE] 当前MTU: $CURRENT_MTU}")
                    LogUtils.debug("[BLE] 数据包长度: ${packet.size}")
                    isSending = false
                } else {
                    LogUtils.debug("[BLE] 数据包已提交写入")
                }
            }
        } catch (e: SecurityException) {
            LogUtils.error("[BLE] 权限异常：${e.message}")
            isSending = false
        } catch (e: IllegalStateException) {
            LogUtils.error("[BLE] GATT状态异常：${e.message}")
            isSending = false
        } catch (e: Exception) {
            LogUtils.error("[BLE] 数据包发送异常", e)
            isSending = false
        }
    }
}