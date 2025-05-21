package com.example.jvsglass.bluetooth.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.ble.BLEConstants.CURRENT_MTU
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.PacketUtils
import com.example.jvsglass.utils.toHexString
import com.example.jvsglass.bluetooth.dual.DualBluetoothManager
import java.lang.ref.WeakReference

class BLEGattClient private constructor(context: Context) {
    companion object {
        @Volatile
        private var INSTANCE: BLEGattClient? = null

        fun getInstance(context: Context): BLEGattClient {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: BLEGattClient(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private var connectedDevice: BluetoothDevice? = null    // 设备引用
    private val contextRef = WeakReference(context)
    private var bluetoothGatt: BluetoothGatt? = null
    private var lastSentMessage: String? = null
    private var isSending = false

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
            LogUtils.info("[BLE-Callback] onConnectionStateChange: status=$status, newState=$newState")
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    if (newState == BluetoothProfile.STATE_CONNECTED) {
                        LogUtils.info("[BLE] 已连接到设备 ${gatt.device.address}")
                        DualBluetoothManager.onDeviceConnected?.invoke(gatt.device)
                        gatt.requestMtu(CURRENT_MTU)    // 连接成功时请求MTU
                    } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                        LogUtils.info("[BLE] GATT_SUCCESS 物理层断开")
                        LogUtils.info("[BLE] 断开时的连接参数：${gatt.device}")
                    }
                }
                else -> {
                    LogUtils.error("[BLE] 连接失败，错误码：$status")
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                LogUtils.error("[BLE] 服务发现失败，状态码：$status")
                return
            }

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
                    val message = PacketUtils.processPacket(value)
                    messageListener?.onMessageReceived(message)
                } catch (e: Exception) {
                    LogUtils.error("[BLE] 消息解析异常，数据：${value.toHexString()}", e)
                }
            }
        }

        // MTU变化处理
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            LogUtils.info("[BLE-Callback] onMtuChanged: status=$status, newMTU=$mtu")
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
            LogUtils.info("[BLE] onCharacteristicWrite 状态码：$status")
            if (status == BluetoothGatt.GATT_SUCCESS) {
                LogUtils.debug("[BLE] 数据包已成功发送")
                isSending = false
                lastSentMessage?.let { msg ->
                    messageListener?.onMessageSent(msg)
                    lastSentMessage = null
                }
            } else {
                LogUtils.error("[BLE] 数据包发送失败，状态码：$status")
                isSending = false
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectToDevice(device: BluetoothDevice) {
        LogUtils.info("[BLE] 尝试连接设备 ${device.address}")
        LogUtils.info("[BLE] 开始创建GATT连接，传输模式：${BluetoothDevice.TRANSPORT_LE}")
        connectedDevice = device // 保存设备对象
        val ctx = contextRef.get() ?: run {
            LogUtils.error("[BLE] 上下文已释放，无法创建GATT连接")
            return
        }
        bluetoothGatt = device.connectGatt(ctx, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE).also {
            if (it == null) {
                LogUtils.error("[BLE] connectGatt返回空对象，可能达到连接数限制")
            } else {
                LogUtils.debug("[BLE] GATT对象创建成功：${it.device.address}")
            }
        }
        saveDeviceAddress(device.address) // 连接时保存设备地址
    }

    /*********************
    // 正常断开（保留存储）
    disconnect()
    // 断开并清除存储
    disconnect(shouldClearSavedDevice = true)
    *********************/
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnect(clearSavedDevice: Boolean = false) {
        bluetoothGatt?.let {
            try {
                Handler(Looper.getMainLooper()).postDelayed({
                    bluetoothGatt?.disconnect()
                    bluetoothGatt?.close()
                    bluetoothGatt = null
                    isSending = false // 重置发送状态
                    if (clearSavedDevice) {
                        clearSavedDeviceAddress()
                    }
                    LogUtils.debug("[BLE] 资源完全释放完成")
                }, 500) // 增加延迟确保异步操作完成
                LogUtils.info("[BLE] 连接已主动断开")
            } catch (e: Exception) {
                // 处理权限被拒绝的情况
                LogUtils.error("[BLE] 断开异常: ${e.javaClass.simpleName}")
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendMessage(message: String) {
        lastSentMessage = message

        val packet = PacketUtils.createPacket(message)
        LogUtils.info("[BLE] 发送消息 $message")
        sendPacket(packet)
    }

    // 分包发送逻辑
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendPacket(packet: ByteArray) {
        val context = contextRef.get() ?: run {
            LogUtils.error("[BLE] 上下文已释放")
            isSending = false
            return
        }

        val gatt = bluetoothGatt ?: run {
            LogUtils.error("[BLE] GATT连接不可用")
            isSending = false
            return
        }

        val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val device = connectedDevice ?: run {
            LogUtils.error("[BLE] 设备引用丢失")
            isSending = false
            return
        }

        when (bluetoothManager.getConnectionState(device, BluetoothProfile.GATT)) {
            BluetoothProfile.STATE_CONNECTED -> {
                LogUtils.debug("[BLE] 设备连接正常 (${device.address})")
            }
            BluetoothProfile.STATE_DISCONNECTED -> {
                LogUtils.error("[BLE] 设备已断开连接")
                isSending = false
                return
            }
            else -> {
                LogUtils.error("[BLE] 设备连接状态异常")
                isSending = false
                return
            }
        }

        val service = gatt.getService(BLEConstants.SERVICE_UUID) ?: run {
            LogUtils.error("[BLE] 服务不可用")
            isSending = false
            return
        }

        val characteristic = service.getCharacteristic(BLEConstants.CHARACTERISTIC_UUID) ?: run {
            LogUtils.error("[BLE] 特征值不可用")
            isSending = false
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
                isSending = false
                return
            }
        }

        LogUtils.info("[BLE] 发送数据包：${packet.toHexString()}")

        try {
            isSending = true
            characteristic.value = packet

            Handler(Looper.getMainLooper()).post {
                if (!gatt.writeCharacteristic(characteristic)) {
                    LogUtils.error("[BLE] 写入特征值失败")
                    LogUtils.debug("[BLE] 特征值UUID: ${characteristic.uuid}")
                    LogUtils.debug("[BLE] 当前MTU: $CURRENT_MTU")
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

    // 存储/读取设备地址
    @SuppressLint("UseKtx")
    private fun saveDeviceAddress(address: String) {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", address)?.apply()
    }

    private fun getDeviceAddress(): String? {
        return contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)
            ?.getString("last_connected_device", null)
    }

    @SuppressLint("UseKtx")
    private fun clearSavedDeviceAddress() {
        contextRef.get()?.getSharedPreferences("ble_prefs", Context.MODE_PRIVATE)?.edit()
            ?.putString("last_connected_device", null)?.apply()
    }
}