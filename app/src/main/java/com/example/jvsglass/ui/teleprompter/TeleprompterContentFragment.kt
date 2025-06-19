package com.example.jvsglass.ui.teleprompter

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ScrollView
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.core.view.GestureDetectorCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.BLEClient
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.bluetooth.PacketCommandUtils
import com.example.jvsglass.bluetooth.PacketCommandUtils.CLOSE_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.CMDKey
import com.example.jvsglass.bluetooth.PacketCommandUtils.OPEN_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.createPacket
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

interface TeleprompterControl {
    fun toggleVoiceControl()
    fun toggleAutoScroll()
    fun toggleRemoteControl()
}

interface ControlUiCallback {
    fun onAutoFinished()
    fun onVoiceScrollStarted(isStart: Boolean)
    fun onUniformScrollStarted(isStart: Boolean)
    fun onRemoteScrollStarted(isStart: Boolean)
}

class TeleprompterContentFragment : Fragment(), TeleprompterControl {
    companion object {
        fun newInstance(name: String, date: String, content: String) = TeleprompterContentFragment().apply {
            arguments = Bundle().apply {
                putString("fileName", name)
                putString("fileDate", date)
                putString("fileContent", content)
            }
        }
    }

    private lateinit var voiceManager: VoiceManager
    private lateinit var realtimeAsrClient: RealtimeAsrClient
    private val vm: TeleprompterViewModel by activityViewModels()

    private var currentVoicePath = ""
    private val bleClient by lazy { BLEClient.getInstance(requireContext()) }
    private val vibrator by lazy { requireContext().getSystemService(Context.VIBRATOR_SERVICE) as Vibrator }
    private var totalLines = 0
    private var scrollLines = 0
    private var fileContent = ""
    private var isAtTopOrBottom = false
    private lateinit var gestureDetector: GestureDetectorCompat
    private var autoScrollJob: Job? = null
    private var scrollIntervalMs = 15_000L
    private var micControl = true
    private var remoteControl = true
    private var lastProcessedLength = 0
    private var lastSentBlock: String = ""
    private var uiCb: ControlUiCallback? = null

    private var recordingFilePath: String? = null

    private lateinit var scrollView: ScrollView
    private lateinit var tvContent: TextView

    private val frameSize = 1280

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
            requireActivity().finish()
        }
    }

    private val settingLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val newMs = result.data?.getLongExtra("scrollIntervalMs", scrollIntervalMs)
            if (newMs != null) scrollIntervalMs = newMs
        }
        vm.clearRequestSettings()
    }

    @SuppressLint("MissingPermission")
    private val scrollDetector = VerticalScrollDetector { deltaY ->
        showScrollResult(deltaY)
    }

    @SuppressLint("ClickableViewAccessibility")
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun toggleVoiceControl() {
        if (micControl) {
            WarningDialog.showDialog(
                context = requireContext(),
                title = "动态滚动使用提示",
                message = """
                        本功能依赖语音识别技术，环境噪音或口音可能导致偶发误识别。
                        建议在相对安静的环境下使用，并尽量保持吐字清晰。
                        我们将持续优化体验，感谢理解与支持！
                    """.trimIndent(),
                positiveButtonText = "开始使用",
                negativeButtonText = "暂不使用",
                listener = object : WarningDialog.DialogButtonClickListener {
                    @SuppressLint("ClickableViewAccessibility")
                    @RequiresPermission(
                        allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT]
                    )
                    override fun onPositiveButtonClick() {
                        uiCb?.onVoiceScrollStarted(true)

                        realtimeAsrClient.connect()
                        realtimeAsrClient.keepConnectionOpen()

                        scrollView.setOnTouchListener(disabledTouchListener)

                        lifecycleScope.launch {
                            BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, OPEN_MIC))

                            delay(300)

                            val currentSplit = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
                            sendMessage(currentSplit.sendBlock)
                        }
                        currentVoicePath = voiceManager.startRecording(object : VoiceManager.AudioRecordCallback {
                            override fun onAudioData(data: ByteArray) {
                                realtimeAsrClient.sendAudio(data)
                            }
                        }).toString()

//                            val file = voiceManager.startBtRecording()
//                            LogUtils.info("start recording, file: $file")
////
//                            val buffer = ByteArrayOutputStream()
//                            BluetoothConnectManager.onAudioStreamReceived = {data ->
////                                realtimeAsrClient.appendAudio(data)
//                                LogUtils.info("audio data, size: ${data.size}, data: ${data.toHexString()}")
//
//                                voiceManager.feedBtData(data)
//
//                                buffer.write(data)
//                                while (buffer.size() >= frameSize) {
//                                    val chunk = buffer.toByteArray().copyOfRange(0, frameSize)
//                                    realtimeAsrClient.sendAudio(chunk)
//
//                                    val leftover = buffer.toByteArray().copyOfRange(frameSize, buffer.size())
//                                    buffer.reset()
//                                    buffer.write(leftover)
//                                }
//                            }
                    }

                    override fun onNegativeButtonClick() { micControl = true }
                })
        } else {
            uiCb?.onVoiceScrollStarted(false)

            voiceManager.stopRecording()
            voiceManager.deleteVoiceFile(currentVoicePath)
//                voiceManager.stopBtRecording()
//                BluetoothConnectManager.onAudioStreamReceived = null

            realtimeAsrClient.disconnect()

            scrollView.setOnTouchListener(manualTouchListener)

            BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, CLOSE_MIC))
        }
        micControl = !micControl
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("ClickableViewAccessibility")
    override fun toggleAutoScroll() {
        if (autoScrollJob?.isActive == true) {
            autoScrollJob?.cancel()
            scrollView.setOnTouchListener(manualTouchListener)
            uiCb?.onUniformScrollStarted(false)
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
            ToastUtils.show(requireContext(), "开始滚动 ${scrollIntervalMs/1000} 秒/行")
            uiCb?.onUniformScrollStarted(true)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun toggleRemoteControl() {
        if (remoteControl) {
            scrollView.setOnTouchListener(disabledTouchListener)
            uiCb?.onRemoteScrollStarted(true)
        } else {
            scrollView.setOnTouchListener(manualTouchListener)
            uiCb?.onRemoteScrollStarted(false)
        }
        remoteControl = !remoteControl
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN
        ]
    )
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)

        voiceManager = VoiceManager(requireContext())
        BluetoothConnectManager.initialize(requireContext(), voiceManager)

        initSetting()
        initRealtimeAsrClient()

        setupClientCallbacks()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_teleprompter_content, container, false)

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT]
    )
    @SuppressLint("ClickableViewAccessibility")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.tv_title).text = requireArguments().getString("fileName").orEmpty()
        view.findViewById<TextView>(R.id.tv_date).text = requireArguments().getString("fileDate").orEmpty()
        fileContent = requireArguments().getString("fileContent").orEmpty()

        tvContent = view.findViewById(R.id.tv_content)
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, 0)
        tvContent.text = splitResult.displayBlock
        totalLines = splitResult.totalLines

        gestureDetector = GestureDetectorCompat(requireContext(), object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                super.onLongPress(e)
                val intent = Intent(requireContext(), TeleprompterNewFileActivity::class.java).apply {
                    putExtra("fileName", view.findViewById<TextView>(R.id.tv_title).text.toString())
                    putExtra("fileDate", view.findViewById<TextView>(R.id.tv_date).text.toString())
                    putExtra("fileContent", fileContent)
                }
                editFileLauncher.launch(intent)
            }
        })

        scrollView = view.findViewById(R.id.scrollView)
        scrollView.setOnTouchListener(manualTouchListener)

        vm.requestSettings.observe(viewLifecycleOwner) { req ->
            if (req == null) return@observe
            if (autoScrollJob?.isActive == true || !micControl) {
                ToastUtils.show(requireContext(), "请先“停止滚动”")
            } else {
                val intent = Intent(requireContext(), TeleprompterSettingActivity::class.java)
                    .putExtra("scrollIntervalMs", scrollIntervalMs)
                settingLauncher.launch(intent)
            }
        }
    }

    private fun initSetting() {
        val prefs = requireActivity().getSharedPreferences("teleprompter_setting", Context.MODE_PRIVATE)
        scrollIntervalMs = prefs.getLong("speed", 15_000L)
    }

    private fun initRealtimeAsrClient() {
        realtimeAsrClient = NetworkManager.getInstance()
            .createRealtimeAsrClient(object : RealtimeAsrClient.RealtimeAsrCallback {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onPartialResult(text: String) {
                    requireActivity().runOnUiThread { handleDynamicScroll(text) }
                }

                override fun onFinalResult(text: String) {
                    requireActivity().runOnUiThread { voiceManager.deleteVoiceFile(currentVoicePath) }
                }

                override fun onError(error: String) {
                    requireActivity().runOnUiThread { LogUtils.error(error) }
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
                        ToastUtils.show(requireContext(), "已滚动到末尾")
                        uiCb?.onAutoFinished()
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
        sendMessage(sendBlock)
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
        BluetoothConnectManager.sendMessage(fileContent)
    }

    private fun setupClientCallbacks() {
        bleClient.messageListener = object : BLEClient.MessageListener {
            @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
            override fun onMessageReceived(value: ByteArray) {
                if (!remoteControl) {
                    val command = PacketCommandUtils.parseKeyValuePacket(value)
                    LogUtils.info("[TeleprompterDisplayActivity] command类型：$command")
                    when (command) {
                        PacketCommandUtils.RemoteControlKeyValue.KEY_PREV -> {
                            scrollLines = (scrollLines - 1).coerceAtLeast(0)
                            updateDisplay()
                        }
                        PacketCommandUtils.RemoteControlKeyValue.KEY_NEXT -> {
                            scrollLines = (scrollLines + 1).coerceAtMost(totalLines - 1)
                            updateDisplay()
                        }
                    }
                }
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updateDisplay() {
        val splitResult = SmartTextScroller.splitIntoBlocks(fileContent, scrollLines)
        tvContent.text = splitResult.displayBlock
        sendMessage(splitResult.sendBlock)
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is ControlUiCallback) uiCb = context
    }

    override fun onDetach() {
        super.onDetach()
        uiCb = null
    }
}