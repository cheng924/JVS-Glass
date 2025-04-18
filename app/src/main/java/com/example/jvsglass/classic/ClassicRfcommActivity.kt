package com.example.jvsglass.classic

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.media.MediaRecorder
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.classic.ClassicConstants.MAX_HISTORY_SIZE
import com.example.jvsglass.classic.ClassicConstants.REQUEST_DISCOVERABLE
import com.example.jvsglass.classic.ClassicConstants.SERVER_DISCOVERABLE_TIME
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ClassicRfcommActivity : AppCompatActivity() {

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var server: ClassicRfcommServer
    private lateinit var client: ClassicRfcommClient
    private val discoveredDevices = mutableListOf<BluetoothDevice>()
    private lateinit var deviceListAdapter: ArrayAdapter<String>
    private val messageHistory = mutableListOf<MessageItem>()
    private lateinit var messageAdapter: MessageAdapter
    private lateinit var core: BluetoothConnectionCore
    private var connectedDeviceName: String? = null

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null

    @SuppressLint("MissingPermission")
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                    device?.let {
                        val typeName = client.getDeviceTypeName(device)
                        LogUtils.info("[BluetoothActivity] 发现设备: ${device.name} (${device.address}) 类型: $typeName")
                        if (!discoveredDevices.contains(it) && it.name?.isNotEmpty() == true) {
                            if (device.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO) {
                                discoveredDevices.add(it)
                                deviceListAdapter.add("${it.name} (${it.address})")
                            }
                        }
                    }
                }
                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                    LogUtils.info("[BluetoothActivity] 设备搜索完成")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_classic_rfcomm)

        setupBluetooth()
        setupUI()
        setupRecyclerView()
    }

    private val bondReceiver = object: BroadcastReceiver() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onReceive(c: Context, i: Intent) {
            val dev = i.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
            val state = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)
            if (dev.address == client.pendingAddress && state == BluetoothDevice.BOND_BONDED) {
                // 配对完成，真正去连
                client.connectToDevice(dev)
            }
        }
    }

    @SuppressLint("SetTextI18n")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupBluetooth() {
        val statusText = findViewById<TextView>(R.id.statusText)
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter().also {
            LogUtils.info("[BluetoothActivity] 蓝牙适配器已初始化，启用状态: ${it?.isEnabled}")
        }

        core = BluetoothConnectionCore(object : BluetoothCallback {
            override fun onConnectionSuccess(deviceName: String) = runOnUiThread {
                connectedDeviceName = deviceName
                statusText.text = "已连接到: $deviceName"
                if (deviceName == "Server") requestDiscoverability() // 服务端启动时请求可发现性
            }

            override fun onConnectionFailed(message: String) = runOnUiThread {
                statusText.text = "连接失败: $message"
                LogUtils.error("[BluetoothActivity] 连接失败: $message")
            }

            override fun onMessageReceived(message: String) = runOnUiThread {
                if (message != "ACK")
                    addMessageToHistory("[收到] $message")
            }

            override fun onVoiceMessageReceived(voiceData: ByteArray) {
                try {
                    val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
                    val fileName = "audio_$timestamp.pcm"
                    val tempFile = File(externalCacheDir ?: cacheDir, fileName)
                    tempFile.writeBytes(voiceData)
                    LogUtils.info("[BluetoothActivity] 语音文件保存路径: ${tempFile.absolutePath}, 大小: ${tempFile.length()} 字节")
                    addMessageToHistory("[收到] 语音消息", tempFile.absolutePath)
                } catch (e: IOException) {
                    LogUtils.error("[BluetoothActivity] 语音文件保存失败: ${e.message}")
                }
            }

            override fun onDisconnected() = runOnUiThread { statusText.text = "断连" }
        })

        checkPermissions()
        enableBluetooth()

        // 先注册 “扫描用” receiver
        registerReceiver(receiver, IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
        })
        // 再注册 “配对状态” receiver
        registerReceiver(bondReceiver, IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED))

        server = ClassicRfcommServer(bluetoothAdapter, core, core.callback)
        client = ClassicRfcommClient(bluetoothAdapter, core, core.callback)
    }

    // 检查权限
    private fun checkPermissions() {
        val permissions = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.any { ActivityCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(this, permissions, ClassicConstants.REQUEST_LOCATION)
        }
    }

    // 启用蓝牙
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableBluetooth() {
        if (!bluetoothAdapter.isEnabled) {
            val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableIntent, ClassicConstants.REQUEST_ENABLE_BT)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupUI() {
        val deviceListView = findViewById<ListView>(R.id.deviceListView)
        val btnStartServer = findViewById<Button>(R.id.btnStartServer)
        val btnScanDevices = findViewById<Button>(R.id.btnScanDevices)
        val btnSendMessage = findViewById<Button>(R.id.btnSendMessage)
        val btnRecordVoice = findViewById<Button>(R.id.btnRecordVoice)
        val inputMessage = findViewById<EditText>(R.id.inputMessage)

        findViewById<Button>(R.id.btnBack).setOnClickListener {
            onBackPressed()
        }

        findViewById<Button>(R.id.btnDisconnect).setOnClickListener {
            // 手动断开
            client.disconnect()
            server.cancel()
            core.shutdown()
            finish()
        }

        deviceListAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        deviceListView.adapter = deviceListAdapter
        deviceListView.setOnItemClickListener { _, _, position, _ -> client.connectToDevice(discoveredDevices[position]) }

        btnStartServer.setOnClickListener { server.start() }
        btnScanDevices.setOnClickListener {
            discoveredDevices.clear()
            deviceListAdapter.clear()
            client.startDiscovery()
        }
        btnSendMessage.setOnClickListener {
            val message = inputMessage.text.toString()
            if (message.isNotEmpty()) {
                LogUtils.info("[BluetoothActivity] 发送消息: $message")
                if (client.isConnected()) {
                    addMessageToHistory("[发送] $message")
                    client.sendMessage(message)
                } else if (server.isConnected()) {
                    addMessageToHistory("[发送] $message")
                    server.sendMessage(message)
                }
                inputMessage.text.clear()
            }
        }
        btnRecordVoice.setOnClickListener {
            if (mediaRecorder == null) {
                startRecording()
                btnRecordVoice.text = "停止录音"
            } else {
                stopRecording()
                if (client.isConnected()) {
                    client.sendVoiceMessage(audioFilePath!!)
                } else if (server.isConnected()) {
                    server.sendVoiceMessage(audioFilePath!!)
                }
                btnRecordVoice.text = "录音"
                LogUtils.info("[BluetoothActivity] 发送语音消息成功")
                addMessageToHistory("[发送] 语音消息", audioFilePath)
            }
        }
    }

    private fun requestDiscoverability() {
        val discoverableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE).apply {
            putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, SERVER_DISCOVERABLE_TIME)
        }
        startActivityForResult(discoverableIntent, REQUEST_DISCOVERABLE)
        LogUtils.info("[BluetoothActivity] 请求使设备可被发现")
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
        unregisterReceiver(bondReceiver)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (connectedDeviceName != null) {
            setResult(RESULT_OK, Intent().apply {
                putExtra("CONNECTED_DEVICE", connectedDeviceName)
            })
        } else {
            setResult(RESULT_CANCELED)  // 用户没连上，则告知取消/失败
        }
        super.onBackPressed()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_DISCOVERABLE) {
            if (resultCode == RESULT_OK) {
                LogUtils.info("[BluetoothActivity] 设备已可被发现 (RESULT_OK)")
            } else if (resultCode == RESULT_CANCELED) {
                LogUtils.error("[BluetoothActivity] 用户取消使设备可被发现")
            } else if (resultCode > 0) {
                LogUtils.info("[BluetoothActivity] 设备已可被发现，持续时间: $resultCode 秒")
                val scanMode = bluetoothAdapter.scanMode
                if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
                    LogUtils.info("[BluetoothActivity] 确认设备可被发现 (SCAN_MODE_CONNECTABLE_DISCOVERABLE)")
                } else {
                    LogUtils.error("[BluetoothActivity] 设备未能被发现，扫描模式: $scanMode")
                }
            } else {
                LogUtils.error("[BluetoothActivity] 设备可发现请求失败，结果码: $resultCode")
            }
        }
    }

    private fun startRecording() {
        if (externalCacheDir == null) {
            LogUtils.error("[BluetoothActivity] 外部缓存目录不可用")
            return
        }
        audioFilePath = "${externalCacheDir!!.absolutePath}/audio_record.pcm"
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            setOutputFile(audioFilePath)
            try {
                prepare()
                start()
                LogUtils.info("[BluetoothActivity] 开始录音")
            } catch (e: IOException) {
                LogUtils.error("[BluetoothActivity] 录音准备失败: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply { stop(); release() }
        mediaRecorder = null
    }

    private fun playVoiceMessage(filePath: String) {
        try {
            val mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                LogUtils.info("[BluetoothActivity] 开始播放语音消息")
                ToastUtils.show(this@ClassicRfcommActivity, "正在播放语音消息")
            }
            mediaPlayer.setOnCompletionListener {
                LogUtils.info("[BluetoothActivity] 语音消息播放完成")
                it.release()
            }
        } catch (e: IOException) {
            LogUtils.error("[BluetoothActivity] 播放语音消息失败: ${e.message}")
            ToastUtils.show(this, "播放失败: ${e.message}")
        }
    }

    private fun setupRecyclerView() {
        val recyclerView = findViewById<RecyclerView>(R.id.messageRecyclerView)
        messageAdapter = MessageAdapter(messageHistory) { filePath -> playVoiceMessage(filePath) }
        recyclerView.adapter = messageAdapter
        recyclerView.layoutManager = LinearLayoutManager(this)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun addMessageToHistory(message: String, voiceFilePath: String? = null) {
        val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        val messageWithTime = "[$timestamp] $message"
        if (messageHistory.size >= MAX_HISTORY_SIZE) messageHistory.removeAt(0)
        messageHistory.add(MessageItem(messageWithTime, voiceFilePath))
        runOnUiThread { messageAdapter.notifyDataSetChanged() }
    }
}