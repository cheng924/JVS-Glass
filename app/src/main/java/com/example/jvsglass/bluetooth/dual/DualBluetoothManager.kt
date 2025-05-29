package com.example.jvsglass.bluetooth.dual

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.ble.BLEGattClient
import com.example.jvsglass.bluetooth.ble.BleGattModule
import com.example.jvsglass.bluetooth.classic.ClassicRfcommClient
import com.example.jvsglass.bluetooth.classic.ClassicRfcommModule
import com.example.jvsglass.utils.VoiceManager

object DualBluetoothManager {
    private lateinit var appContext: Context
    private lateinit var btAdapter: BluetoothAdapter

    // 外部注册回调
    var onBleDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onClassicDeviceFound: ((BluetoothDevice) -> Unit)? = null
    var onDeviceConnected: ((BluetoothDevice) -> Unit)? = null
    var onMessageReceived: ((ByteArray) -> Unit)? = null
    var onVoiceReceived: ((ByteArray) -> Unit)? = null

    private lateinit var classicCallback: ClassicRfcommClient.BluetoothCallback
    private lateinit var bleClientListener: BLEGattClient.MessageListener

    fun initialize(
        context: Context,
        bluetoothAdapter: BluetoothAdapter,
        voiceManager: VoiceManager? = null
    ) {
        appContext = context.applicationContext
        btAdapter = bluetoothAdapter

        bleClientListener = object : BLEGattClient.MessageListener {
            override fun onMessageReceived(value: ByteArray) {
                onMessageReceived?.invoke(value)
            }
        }
        BleGattModule.initialize(appContext)

        classicCallback = object : ClassicRfcommClient.BluetoothCallback {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onConnectionSuccess(deviceName: String) {
                btAdapter.bondedDevices.find { it.name == deviceName }?.let {
                    onDeviceConnected?.invoke(it)
                }
            }
            override fun onConnectionFailed(message: String) {  }
            override fun onVoiceMessageReceived(voiceData: ByteArray) {
                onVoiceReceived?.invoke(voiceData)
            }

            override fun onAudioStreamReceived(streamData: ByteArray) {
                voiceManager?.playStreamingAudio(streamData)
            }

            override fun onDisconnected() {  }
        }
        ClassicRfcommModule.initialize(btAdapter, classicCallback)
    }

    @RequiresPermission(allOf = [Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT])
    fun stop() {
        BleGattModule.stopScan()
        BleGattModule.disconnectClient()
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
        BleGattModule.startScan { device ->
            onBleDeviceFound?.invoke(device)
        }
        ClassicRfcommModule.startDiscovery()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun connectBle(device: BluetoothDevice) {
        BleGattModule.connectAsClient(device, bleClientListener)
    }

    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectClassic(device: BluetoothDevice) {
        ClassicRfcommModule.connectAsClient(device)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun sendText(message: String) {
        BleGattModule.sendClientMessage(message)
    }
}