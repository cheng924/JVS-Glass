package com.example.jvsglass.activities.teleprompter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.GestureDetectorCompat
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.ble.BLEGattClient
import com.example.jvsglass.dialog.WarningDialog
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.network.RealtimeAsrClient
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs

class TeleprompterDisplayActivity : AppCompatActivity() {
    private lateinit var voiceManager: VoiceManager
    private lateinit var realtimeAsrClient: RealtimeAsrClient

    private var currentVoicePath = ""
    private val bleClient by lazy { BLEGattClient.getInstance(this) }
    private val vibrator by lazy { getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private var totalLines = 0
    private var scrollLines = 0
    private var fileContent = ""
    private var isAtTopOrBottom = false
    private lateinit var gestureDetector: GestureDetectorCompat
    private var autoScrollJob: Job? = null
    private var scrollIntervalMs = 15_000L
    private var micControl = true
    private var lastProcessedLength = 0
    private var lastSentBlock: String = ""

    private lateinit var scrollView: ScrollView
    private lateinit var tvContent: TextView

    private lateinit var llVoiceControl: LinearLayout
    private lateinit var ivMicControl: ImageView
    private lateinit var tvMicControl: TextView
    private lateinit var llContinueScroll: LinearLayout
    private lateinit var ivScrollStatus: ImageView
    private lateinit var tvScrollStatus: TextView

    @SuppressLint("ClickableViewAccessibility")
    private val manualTouchListener = View.OnTouchListener { _, event ->
        gestureDetector.onTouchEvent(event)
        scrollDetector.handleTouchEvent(event)
        false
    }

    @SuppressLint("ClickableViewAccessibility")
    private val disabledTouchListener = View.OnTouchListener { _, _ ->
        true
    }

    private val editFileLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            // 新建/编辑保存成功，自己finish掉，回到上一个界面
            finish()
        }
    }

    private val settingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newMs = result.data?.getLongExtra("scrollIntervalMs", scrollIntervalMs)
            if (newMs != null) scrollIntervalMs = newMs
        }
    }

    @SuppressLint("MissingPermission")
    private val scrollDetector = VerticalScrollDetector { deltaY ->
        showScrollResult(deltaY)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT]
    )
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_display)

        voiceManager = VoiceManager(this)

        initSetting()
        initBluetoothConnection()
        initView()
        initRealtimeAsrClient()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initBluetoothConnection() {
        // 检查是否有保存的设备地址，并尝试自动连接
        val prefs = getSharedPreferences("ble_prefs", MODE_PRIVATE)
        val lastDeviceAddress = prefs.getString("last_connected_device_address", null)
        if (lastDeviceAddress != null) {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
            val device = bluetoothAdapter.getRemoteDevice(lastDeviceAddress)
            bleClient.connectToDevice(device) // 触发自动连接
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [Manifest.permission.RECORD_AUDIO,
                Manifest.permission.BLUETOOTH_CONNECT]
    )
    @SuppressLint("ClickableViewAccessibility")
    private fun initView() {
        llVoiceControl = findViewById(R.id.ll_voice_control)
        ivMicControl = findViewById(R.id.iv_mic_control)
        tvMicControl = findViewById(R.id.tv_mic_control)
        llContinueScroll = findViewById(R.id.ll_continue_scroll)
        ivScrollStatus = findViewById(R.id.iv_scroll_status)
        tvScrollStatus = findViewById(R.id.tv_scroll_status)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<TextView>(R.id.tv_title).text = intent.getStringExtra("fileName") ?: ""
        findViewById<TextView>(R.id.tv_date).text = intent.getStringExtra("fileDate") ?: ""
        fileContent = intent.getStringExtra("fileContent") ?: ""

        tvContent = findViewById(R.id.tv_content)
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, 0)
        tvContent.text = splitResult.displayBlock
        totalLines = splitResult.totalLines

        gestureDetector = GestureDetectorCompat(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                val intent = Intent(this@TeleprompterDisplayActivity, TeleprompterNewFileActivity::class.java).apply {
                    putExtra("fileName", findViewById<TextView>(R.id.tv_title).text.toString())
                    putExtra("fileDate", findViewById<TextView>(R.id.tv_date).text.toString())
                    putExtra("fileContent", fileContent)
                }
                editFileLauncher.launch(intent)
            }
        })

        scrollView = findViewById(R.id.scrollView)
        scrollView.setOnTouchListener(manualTouchListener)

        findViewById<ImageView>(R.id.teleprompter_settings).setOnClickListener {
            if (autoScrollJob?.isActive == true || !micControl) {
                ToastUtils.show(this, "请先“停止滚动”")
                return@setOnClickListener
            }
            val intent = Intent(this, TeleprompterSettingActivity::class.java)
                .putExtra("scrollIntervalMs", scrollIntervalMs)
            settingLauncher.launch(intent)
        }

        llVoiceControl.setOnClickListener {
            if (micControl) {
                WarningDialog.showDialog(
                    context = this@TeleprompterDisplayActivity,
                    title = "动态滚动使用提示",
                    message = """
                        本功能依赖语音识别技术，环境噪音或口音可能导致偶发误识别。
                        建议在相对安静的环境下使用，并尽量保持吐字清晰。
                        我们将持续优化体验，感谢理解与支持！
                    """.trimIndent(),
                    positiveButtonText = "开始使用",
                    negativeButtonText = "暂不使用",
                    listener = object : WarningDialog.DialogButtonClickListener {
                        @RequiresPermission(Manifest.permission.RECORD_AUDIO)
                        override fun onPositiveButtonClick() {
                            tvMicControl.text = "停止滚动"
                            ivMicControl.setImageResource(R.drawable.ic_mic_off)
                            llVoiceControl.setBackgroundResource(R.drawable.rounded_button_selected)
                            llContinueScroll.isClickable = false

                            realtimeAsrClient.connect()
                            realtimeAsrClient.keepConnectionOpen()

                            scrollView.setOnTouchListener(disabledTouchListener)

                            val currentSplit = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
                            if (ActivityCompat.checkSelfPermission(
                                    this@TeleprompterDisplayActivity,
                                    Manifest.permission.BLUETOOTH_CONNECT
                                ) == PackageManager.PERMISSION_GRANTED
                            ) {
                                sendMessage(currentSplit.sendBlock)
                            } else {
                                LogUtils.warn("缺少 BLUETOOTH_CONNECT 权限，无法发送 teleprompter 内容")
                            }

                            currentVoicePath = voiceManager.startRecording(object : VoiceManager.AudioRecordCallback {
                                override fun onAudioData(data: ByteArray) {
                                    realtimeAsrClient.sendAudioChunk(data)
                                }
                            }).toString()
                        }

                        override fun onNegativeButtonClick() { micControl = true }
                    })
            } else {
                tvMicControl.text = "动态滚动"
                ivMicControl.setImageResource(R.drawable.ic_mic_on)
                llVoiceControl.setBackgroundResource(R.drawable.rounded_button)
                llContinueScroll.isClickable = true

                voiceManager.stopRecording()
                voiceManager.deleteVoiceFile(currentVoicePath)
                realtimeAsrClient.disconnect()

                scrollView.setOnTouchListener(manualTouchListener)
            }
            micControl = !micControl
        }

        llContinueScroll.setOnClickListener {
            if (autoScrollJob?.isActive == true) {
                autoScrollJob?.cancel()
                scrollView.setOnTouchListener(manualTouchListener)
                tvScrollStatus.text = "匀速滚动"
                ivScrollStatus.setImageResource(R.drawable.ic_continue)
                llContinueScroll.setBackgroundResource(R.drawable.rounded_button)
                llVoiceControl.isClickable = true
            } else {
                if (scrollLines == totalLines - 1) {
                    scrollLines = 0
                    // 重置文本块
                    val split0 = SmartTextScroller.splitIntoBlocks(fileContent, 0)
                    tvContent.text = split0.displayBlock
                    scrollView.scrollTo(0, 0)
                    sendMessage(split0.sendBlock)
                }
                val currentSplit = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
                sendMessage(currentSplit.sendBlock)

                scrollView.setOnTouchListener(disabledTouchListener)
                startAutoScroll(scrollIntervalMs)
                ToastUtils.show(this, "开始滚动 ${scrollIntervalMs/1000} 秒/行")
                tvScrollStatus.text = "停止滚动"
                ivScrollStatus.setImageResource(R.drawable.ic_suspend)
                llContinueScroll.setBackgroundResource(R.drawable.rounded_button_selected)
                llVoiceControl.isClickable = false
            }
        }
    }

    private fun initSetting() {
        val prefs = getSharedPreferences("teleprompter_setting", Context.MODE_PRIVATE)
        scrollIntervalMs = prefs.getLong("speed", 15_000L)
    }

    private fun initRealtimeAsrClient() {
        realtimeAsrClient = NetworkManager.getInstance()
            .createRealtimeAsrClient(object : RealtimeAsrClient.RealtimeAsrCallback {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onPartialResult(text: String) {
                    runOnUiThread { handleDynamicScroll(text) }
                }

                override fun onFinalResult(text: String) {
                    runOnUiThread { voiceManager.deleteVoiceFile(currentVoicePath) }
                }

                override fun onError(error: String) {
                    runOnUiThread { LogUtils.error(error) }
                }

                override fun onConnectionChanged(connected: Boolean) {
                    LogUtils.info("ASR连接状态: $connected")
                }

                override fun onSessionReady() {
                    LogUtils.info("ASR session ready")
                }
            })
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startAutoScroll(intervalMs: Long) {
        // 先停掉任何已有的自动滚动
        autoScrollJob?.cancel()
        autoScrollJob = lifecycleScope.launch {
            // 从当前的scrollLines继续
            while (isActive) {
                delay(intervalMs)
                // 计算下一行的索引，最多到最后一行
                scrollLines = (scrollLines + 1).coerceAtMost(totalLines - 1)
                val split = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
                tvContent.text = split.displayBlock
                scrollView.scrollTo(0, 0)   // 每次重置ScrollView到顶部，确保从第0像素开始

                sendMessage(split.sendBlock)

                if (scrollLines == totalLines - 1) {
                    withContext(Dispatchers.Main) {
                        scrollView.setOnTouchListener(manualTouchListener)
                        ToastUtils.show(this@TeleprompterDisplayActivity, "已滚动到末尾")
                        tvScrollStatus.text = "匀速滚动"
                        ivScrollStatus.setImageResource(R.drawable.ic_continue)
                        llContinueScroll.setBackgroundResource(R.drawable.rounded_button)
                        llVoiceControl.isClickable = true
                    }
                    break
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun showScrollResult(deltaY: Float) {
        val scrollPerLine = tvContent.lineHeight.toFloat()  // 每行高度71.0px
        val deltaYAbs = abs(deltaY)
        val halfLine = scrollPerLine / 2

        // 计算实际滚动行数
        val exactLines = deltaYAbs / scrollPerLine
        val lines = when {
            deltaYAbs < halfLine -> 0   // 不足半行不计
            exactLines - exactLines.toInt() >= 0.5 -> exactLines.toInt() + 1  // 超过半行进位
            else -> exactLines.toInt()  // 整行处理
        }

//        val info = """
//            |════════ 滚动监测 ════════
//            |总位移: ${"%.1f".format(abs(deltaY))}px
//            |方向: ${if (deltaY > 0) "↓ 向下" else "↑ 向上"}
//            |滚动行数: $lines 行
//            |设备DPI: ${resources.displayMetrics.densityDpi}dpi
//            |═════════════════════════
//        """.trimMargin()
//        LogUtils.info(info)

        scrollLines = if (deltaY > 0) scrollLines-lines else scrollLines+lines
        scrollLines = scrollLines.coerceIn(0, totalLines - 1)
//        LogUtils.info("滚动行数：$scrollLines 行")
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
//        LogUtils.info("显示的内容：${splitResult.displayBlock}")
//        LogUtils.info("发送的内容：${splitResult.sendBlock}")

        if (scrollLines == 0 || scrollLines == totalLines - 1) {
            if (!isAtTopOrBottom) {
                isAtTopOrBottom = true
                handleAction(splitResult.displayBlock, splitResult.sendBlock)
            }
        } else {
            isAtTopOrBottom = false
            handleAction(splitResult.displayBlock, splitResult.sendBlock)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleAction(displayBlock: String, sendBlock: String) {
        val effect = VibrationEffect.createPredefined(
            VibrationEffect.EFFECT_CLICK // 短点击反馈
        )
        vibrator.vibrate(effect)

        tvContent.text = displayBlock
//        sendMessage(sendBlock)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun handleDynamicScroll(partialText: String) {
        val split = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
        val sendBlock = split.sendBlock
        val lines = sendBlock.split("\n")

        val oldLen = lastProcessedLength
        lastProcessedLength = partialText.length
        var newPart = if (partialText.length > oldLen) partialText.substring(oldLen) else ""
        newPart = newPart.replace(Regex("[,，.。]"), "")
        if (newPart.isEmpty()) return
        LogUtils.info("识别文字：$newPart")

        val matchedLineIndex = lines.indexOfFirst { line ->
            line.contains(newPart)
        }

        if (matchedLineIndex != -1) {
            scrollLines = (scrollLines + matchedLineIndex).coerceAtMost(totalLines - 1)
            val nextSplit = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
            if (lastSentBlock == nextSplit.sendBlock) return

            LogUtils.info("sendBlock：${nextSplit.sendBlock}")
            lastSentBlock = nextSplit.sendBlock
            tvContent.text = nextSplit.displayBlock
            scrollView.scrollTo(0, 0)
            if (nextSplit.sendBlock.isNotEmpty()) { sendMessage(nextSplit.sendBlock) }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun sendMessage(fileContent: String) {
        if (fileContent.isEmpty()) return
        bleClient.sendMessage(fileContent)
    }
}