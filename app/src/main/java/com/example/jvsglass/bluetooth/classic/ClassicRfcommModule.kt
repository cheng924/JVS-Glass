package com.example.jvsglass.bluetooth.classic

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import androidx.annotation.RequiresPermission
import com.example.jvsglass.bluetooth.classic.ClassicRfcommClient.BluetoothCallback

object ClassicRfcommModule {
    private lateinit var adapter: BluetoothAdapter
    private lateinit var client: ClassicRfcommClient

    fun initialize(btAdapter: BluetoothAdapter, callback: BluetoothCallback) {
        adapter = btAdapter
        client = ClassicRfcommClient(adapter, callback)
    }

    /**
     * 开始 Classic 蓝牙设备发现
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startDiscovery() {
        client.startDiscovery()
    }

    /**
     * 连接指定设备为客户端
     */
    @RequiresPermission(
        allOf = [Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN]
    )
    fun connectAsClient(device: BluetoothDevice) {
        client.connectToDevice(device)
    }

    /**
     * 断开客户端连接
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun disconnectClient() {
        client.disconnect()
    }
}