package com.example.jvsglass.bluetooth.dual

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.ble.BLEGattClient
import com.example.jvsglass.bluetooth.ble.BleModule
import com.example.jvsglass.bluetooth.classic.BluetoothConnectionCore
import com.example.jvsglass.bluetooth.classic.ClassicRfcommModule

object DualBluetoothManager {
    private lateinit var appContext: Context
    private lateinit var btAdapter: BluetoothAdapter

    // 外部注册回调
    var onServerStarted: (() -> Unit)? = null
    var onBleDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onClassicDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null
    var onMessageReceived: ((String) -> Unit)? = null
    var onVoiceReceived: ((ByteArray) -> Unit)? = null

    private lateinit var classicCallback: BluetoothConnectionCore.BluetoothCallback
    private lateinit var bleClientListener: BLEGattClient.MessageListener

    fun initialize(
        context: Context,
        bluetoothAdapter: BluetoothAdapter
    ) {
        appContext = context.applicationContext
        btAdapter = bluetoothAdapter

        bleClientListener = object : BLEGattClient.MessageListener {
            override fun onMessageReceived(message: String) {
                onMessageReceived?.invoke(message)
            }
            override fun onMessageSent(message: String) {  }
        }
        BleModule.initialize(appContext)

        classicCallback = object : BluetoothConnectionCore.BluetoothCallback {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionSuccess(deviceName: String) {
                btAdapter.bondedDevices.find { it.name == deviceName }?.let {
                    onDeviceConnected?.invoke(it)
                }
            }
            override fun onConnectionFailed(message: String) {  }
            override fun onMessageReceived(message: String) {
                onMessageReceived?.invoke(message)
            }
            override fun onVoiceMessageReceived(voiceData: ByteArray) {
                onVoiceReceived?.invoke(voiceData)
            }
            override fun onDisconnected() {  }
        }
        ClassicRfcommModule.initialize(btAdapter, classicCallback)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun stop() {
        BleModule.stopScan()
        BleModule.disconnectClient()
        ClassicRfcommModule.disconnectClient()
    }

    @RequiresPermission(
        allOf = [
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_CONNECT
        ]
    )
    fun startAsClient() {
        BleModule.startScan { device ->
            onBleDeviceFound?.invoke(device)
        }
        ClassicRfcommModule.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectBle(device: BluetoothDevice) {
        BleModule.connectAsClient(device, bleClientListener)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectClassic(device: BluetoothDevice) {
        ClassicRfcommModule.connectAsClient(device)
    }

    /** 发送文本 */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendText(message: String) {
        BleModule.sendClientMessage(message)
    }
}