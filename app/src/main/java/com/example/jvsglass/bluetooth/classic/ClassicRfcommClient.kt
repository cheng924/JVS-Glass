package com.example.jvsglass.bluetooth.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.classic.ClassicConstants.CONNECT_TIMEOUT_MS
import com.example.jvsglass.bluetooth.classic.ClassicConstants.UUID_RFCOMM
import com.example.jvsglass.bluetooth.classic.BluetoothConnectionCore.ConnectionState.CONNECTING
import com.example.jvsglass.bluetooth.classic.BluetoothConnectionCore.ConnectionState.CONNECTED
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.bluetooth.dual.DualBluetoothManager
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

class ClassicRfcommClient(
    private val adapter: BluetoothAdapter,
    private val core: BluetoothConnectionCore,
    private val callback: BluetoothConnectionCore.BluetoothCallback
) {
    private var clientSocket: BluetoothSocket? = null
    private var reconnectAttempts = AtomicInteger(0)
    private val lock = Any()
    private var pendingAddress: String? = null

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        LogUtils.info("[BluetoothClient] 开始设备搜索...")
        if (adapter.isDiscovering) {
            LogUtils.warn("[BluetoothClient] 发现已有搜索正在进行，取消前次搜索...")
            adapter.cancelDiscovery()
        }
        adapter.startDiscovery()
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectToDevice(device: BluetoothDevice) {
        adapter.cancelDiscovery()
        LogUtils.info("[BluetoothClient] 尝试连接设备: ${device.name} (${device.address}) 类型: ${getDeviceTypeName(device)}")
        if (device.bondState != BluetoothDevice.BOND_BONDED) {
            pendingAddress = device.address
            LogUtils.warn("[BluetoothClient] 设备未配对，尝试创建配对...")
            device.createBond() // 发起配对请求
            return
        }
        core.setConnectionState(CONNECTING)
        reconnectAttempts.set(0)
        ConnectThread(device).start()
    }

    // 获取设备CoD大类
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getDeviceMajorClass(device: BluetoothDevice): Int {
        return device.bluetoothClass?.majorDeviceClass ?: BluetoothClass.Device.Major.UNCATEGORIZED
    }

    // 获取可读类型名称
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun getDeviceTypeName(device: BluetoothDevice): String {
        val major = getDeviceMajorClass(device)
        return when (major) {
            BluetoothClass.Device.Major.COMPUTER    -> "Computer"           // 笔记本/台式机
            BluetoothClass.Device.Major.PHONE       -> "Phone"              // 手机
            BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"        // 耳机/音箱
            BluetoothClass.Device.Major.PERIPHERAL  -> "Peripheral"         // 游戏手柄/键鼠
            BluetoothClass.Device.Major.UNCATEGORIZED -> "Uncategorized"    // 通用设备方块
            else -> "Unknown"
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
        private val socket: BluetoothSocket? = device.createRfcommSocketToServiceRecord(UUID_RFCOMM)

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        override fun run() {
            LogUtils.info("[ConnectThread] 开始连接设备: ${device.name} (${device.address})")
            adapter.cancelDiscovery()

            val handler = Handler(Looper.getMainLooper())
            val timeoutRunnable = Runnable {
                LogUtils.error("[ConnectThread] 手动超时，关闭 socket")
                try { socket?.close() } catch (_:IOException){}
                onConnectFailed("连接超时")
            }
            handler.postDelayed(timeoutRunnable, CONNECT_TIMEOUT_MS)

            try {
                LogUtils.info("[ConnectThread] 调用 socket.connect()")
                socket?.connect()
                handler.removeCallbacks(timeoutRunnable)
                LogUtils.info("[ConnectThread] 连接成功")
                onConnectSuccess(device)
            } catch (e:IOException) {
                handler.removeCallbacks(timeoutRunnable)
                LogUtils.error("[ConnectThread] 连接失败: ${e.message}")
                try { socket?.close() } catch (_:IOException){}
                onConnectFailed("连接失败: ${e.message}")
            }
        }

        @RequiresApi(Build.VERSION_CODES.TIRAMISU)
        private fun onConnectSuccess(device: BluetoothDevice) {
            synchronized(lock) {
                clientSocket = socket
                core.setConnectionState(CONNECTED)
            }
            callback.onConnectionSuccess(device.name ?: "Unknown")
            core.manageConnectedSocket(socket!!)
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
        synchronized(lock) {
            core.reconnect(callback, { attempt ->
                reconnectAttempts.set(attempt)
                connectToDevice(device)
            }, reconnectAttempts.get())
        }
    }
}