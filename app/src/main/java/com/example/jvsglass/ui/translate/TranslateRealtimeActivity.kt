package com.example.jvsglass.ui.translate

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.BuildConfig
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.TranslateHistoryEntity
import com.example.jvsglass.database.TranslateItemEntity
import com.example.jvsglass.dialog.WarningDialog
import com.example.jvsglass.network.RealtimeClasiClient
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.bluetooth.PacketCommandUtils.OPEN_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.CLOSE_MIC
import com.example.jvsglass.bluetooth.PacketCommandUtils.ENTER_TRANSLATE
import com.example.jvsglass.bluetooth.PacketCommandUtils.CMDKey
import com.example.jvsglass.bluetooth.PacketCommandUtils.createAIPacket
import com.example.jvsglass.bluetooth.PacketCommandUtils.createPacket
import com.example.jvsglass.bluetooth.PacketCommandUtils.createTranslationPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class TranslateRealtimeActivity : AppCompatActivity() {

    private lateinit var translationAdapter: TranslationAdapter
    private lateinit var tvSourceLanguage: TextView
    private lateinit var tvTargetLanguage: TextView
    private lateinit var rvTranslateResults: RecyclerView
    private lateinit var llTextSetting: LinearLayout
    private lateinit var tvSourceLanguageSetting: TextView
    private lateinit var tvTargetLanguageSetting: TextView
    private lateinit var llTranslateState: LinearLayout
    private lateinit var ivTranslateState: ImageView
    private lateinit var tvTranslateState: TextView
    private var languageStyleState = 0
    private var translateState = 0

    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var voiceManager: VoiceManager
    private lateinit var clasiClient: RealtimeClasiClient
    private var currentSourceText = ""
    private var currentTargetText = ""

    private var recordingFilePath: String? = null
    private var errorDialog: AlertDialog? = null
    private var isManualPause = false
    private var isUpdatingLanguages = false

    private var sendMessage = ""
    private val timestamp = System.currentTimeMillis()

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT]
    )
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_realtime)

        voiceManager = VoiceManager(this)
        BluetoothConnectManager.initialize(this, voiceManager)
        setupUI()
        setupRecyclerView()
        setupButtonStyle()

        if (!isBluetoothHeadsetConnected()) {
            AlertDialog.Builder(this)
                .setTitle("眼镜未连接")
                .setPositiveButton("确定", null)
                .show()
        }

        initClasiClient()

        BluetoothConnectManager.sendCommand(createPacket(CMDKey.INTERFACE_COMMAND, ENTER_TRANSLATE))
//        BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, OPEN_MIC))
    }

    private fun setupUI() {
        tvSourceLanguage = findViewById(R.id.tv_source_language)
        tvTargetLanguage = findViewById(R.id.tv_target_language)
        llTextSetting = findViewById(R.id.ll_text_setting)
        tvSourceLanguageSetting = findViewById(R.id.tv_source_language_setting)
        tvTargetLanguageSetting = findViewById(R.id.tv_target_language_setting)
        llTranslateState = findViewById(R.id.ll_translate_state)
        ivTranslateState = findViewById(R.id.iv_translate_state)
        tvTranslateState = findViewById(R.id.tv_translate_state)
    }

    private fun setupRecyclerView() {
        rvTranslateResults = findViewById(R.id.rv_translate_results)
        rvTranslateResults.layoutManager = LinearLayoutManager(this)
        translationAdapter = TranslationAdapter(this, mutableListOf(), languageStyleState)
        rvTranslateResults.adapter = translationAdapter
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @RequiresPermission(
        allOf = [Manifest.permission.RECORD_AUDIO, Manifest.permission.BLUETOOTH_CONNECT]
    )
    private fun setupButtonStyle() {
        findViewById<LinearLayout>(R.id.ll_stop).setOnClickListener {
            WarningDialog.showDialog(
                context = this@TranslateRealtimeActivity,
                title = "数据存储提示",
                message = "本次翻译是否需要记录？",
                positiveButtonText = "保存",
                negativeButtonText = "取消",
                listener = object : WarningDialog.DialogButtonClickListener {
                    override fun onPositiveButtonClick() {
                        saveHistory()
                        LogUtils.info("[TranslateRealtimeActivity] 录音保存路径：$recordingFilePath")
                    }

                    override fun onNegativeButtonClick() {
                        stopRecording()
                        recordingFilePath?.let { voiceManager.deleteVoiceFile(it) }
                        LogUtils.info("[TranslateRealtimeActivity] 录音已删除: $recordingFilePath")
                        finish()
                        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                    }
                })
            BluetoothConnectManager.sendCommand(createPacket(CMDKey.MIC_COMMAND, CLOSE_MIC))
        }

        findViewById<ImageView>(R.id.iv_convert).setOnClickListener {
            var source = tvSourceLanguage.text.toString()
            var target = tvTargetLanguage.text.toString()

            tvSourceLanguage.text = target
            tvTargetLanguage.text = source

            source = tvSourceLanguageSetting.text.toString()
            target = tvTargetLanguageSetting.text.toString()

            tvSourceLanguageSetting.text = target
            tvTargetLanguageSetting.text = source

            isUpdatingLanguages = true
            stopRecording()
            clasiClient.updateLanguages(
                languageConvert(tvSourceLanguage.text.toString()),
                languageConvert(tvTargetLanguage.text.toString())
            )
        }

        llTextSetting.setOnClickListener {
            languageStyleState = (languageStyleState + 1) % 3
            when (languageStyleState) {
                0 -> {
                    tvSourceLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.white))
                    tvTargetLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
                1 -> {
                    tvSourceLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.white))
                    tvTargetLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.button_text))
                }
                2 -> {
                    tvSourceLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.button_text))
                    tvTargetLanguageSetting.setTextColor(ContextCompat.getColor(this, R.color.white))
                }
            }
            translationAdapter.updateDisplayMode(languageStyleState)
        }

        llTranslateState.setOnClickListener {
            translateState = (translateState + 1) % 2
            when (translateState) {
                0 -> {
                    isManualPause = false
                    ivTranslateState.setImageResource(R.drawable.ic_suspend)
                    tvTranslateState.text = "暂停"
                    clasiClient.setShouldReconnect(true)
                    if (!clasiClient.isConnected()) {
                        clasiClient.connect()
                    }
                    voiceManager.resumeRecording()
                }
                1 -> {
                    isManualPause = true
                    ivTranslateState.setImageResource(R.drawable.ic_continue)
                    tvTranslateState.text = "继续"
                    voiceManager.pauseRecording()
                    clasiClient.setShouldReconnect(false)
                }
            }
        }
    }

    private fun addTranslationResult(source: String, target: String) {
        translationAdapter.addItem(TranslationResult(source, target, isPartial = false))
        rvTranslateResults.post {
            rvTranslateResults.smoothScrollToPosition(translationAdapter.itemCount - 1)
        }
    }

    private fun initClasiClient() {
        clasiClient = RealtimeClasiClient(
            apiKey = BuildConfig.DOUBAO_AI_API_KEY,
            sourceLang = languageConvert(tvSourceLanguage.text.toString()),
            targetLang = languageConvert(tvTargetLanguage.text.toString()),
            callback = object : RealtimeClasiClient.ClasiCallback {
                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onTranscriptUpdate(text: String) {
                    runOnUiThread {
                        currentSourceText += text
                        updatePartialDisplay()
                    }
                }

                @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
                override fun onTranslationUpdate(text: String) {
                    runOnUiThread {
                        currentTargetText += text
                        updatePartialDisplay()
                    }
                }

                override fun onFinalResult(transcript: String, translation: String) {
                    runOnUiThread  {
                        LogUtils.info("收到实时语音识别结果：$transcript, 翻译结果：$translation")
                        addTranslationResult(transcript, translation)
                        currentSourceText = ""
                        currentTargetText = ""
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        if (!isManualPause && !isUpdatingLanguages && !isFinishing && !isDestroyed) {
                            LogUtils.info("连接失败，停止录音")
                            if (errorDialog?.isShowing != true) {
                                errorDialog = AlertDialog.Builder(this@TranslateRealtimeActivity)
                                    .setMessage("网络波动，请稍后")
                                    .setCancelable(false) // 禁止点击外部关闭
                                    .create()
                                errorDialog?.show()
                            }
                            LogUtils.error(error)
                            stopRecording()
                        }
                    }
                }

                override fun onConnectionChanged(connected: Boolean) { }

                @RequiresPermission(Manifest.permission.RECORD_AUDIO)
                override fun onSessionReady() {
                    runOnUiThread {
                        LogUtils.info("连接成功，开始录音")
                        errorDialog?.dismiss()
                        errorDialog = null

                        if (!isFinishing && !isDestroyed) {
                            translateState = 0
                            ivTranslateState.setImageResource(R.drawable.ic_suspend)
                            tvTranslateState.text = "暂停"
                            isUpdatingLanguages = false
                            if (!isManualPause) {
                                startRecording()
                            }
                        }
                    }
                }
            }
        )
        clasiClient.connect()
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        if (!clasiClient.isConnected()) {
            clasiClient.disconnect()
            clasiClient.connect()
            LogUtils.error("连接未就绪，请稍后重试")
            translateState = 1
            return
        }

        recordingFilePath = voiceManager.startStreamingAndRecording(
            object : VoiceManager.AudioRecordCallback{
                override fun onAudioData(data: ByteArray) {
                    // 将音频数据发送到翻译服务
                    if (clasiClient.isConnected()) {
                        clasiClient.sendAudioChunk(data)
                    }
                }
            })
        LogUtils.info("实时语音采集已启动，文件路径：$recordingFilePath")
    }

    private fun stopRecording() {
        if (currentSourceText.isNotEmpty() || currentTargetText.isNotEmpty()) {
            addTranslationResult(currentSourceText, currentTargetText)
            currentSourceText = ""
            currentTargetText = ""
        }

        voiceManager.stopRecording()
        clasiClient.commitAudio() // 通知服务器音频结束
        LogUtils.info("停止录音")
    }

    /**
     * 累积更新显示当前组：
     * 调用 adapter.updatePartialPair 将当前组（累积的 currentSourceText 与 currentTargetText）显示出来；
     * 检查两侧是否都以标点结尾，若是则认为当前组完成，调用 addTranslationResult 固定显示，并重置累积变量。
     */
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun updatePartialDisplay() {
        translationAdapter.updatePartialPair(currentSourceText, currentTargetText)

        when (languageStyleState) {
            0 -> { sendMessage = currentSourceText + "\n" + currentTargetText + "\n" }
            1 -> { sendMessage = currentSourceText + "\n" }
            2 -> { sendMessage = currentTargetText + "\n" }
        }
        LogUtils.info("发送蓝牙消息：$sendMessage")

        if (sendMessage.replace("\\R".toRegex(), "").isNotEmpty()) {
            val packets = createTranslationPacket(sendMessage)
            CoroutineScope(Dispatchers.IO).launch {
                for (packet in packets) {
                    BluetoothConnectManager.sendCommand(packet)
                    delay(10)
                }
            }
            sendMessage = ""
        }

        // 检查当前累积的文本是否都以标点结束
        if (endsWithPunctuation(currentSourceText) && endsWithPunctuation(currentTargetText)) {
            currentSourceText = ""
            currentTargetText = ""
        }
        rvTranslateResults.post {
            rvTranslateResults.smoothScrollToPosition(translationAdapter.itemCount - 1)
        }
    }

    private fun saveHistory() {
        val session = TranslateHistoryEntity(
            timestamp = timestamp,
            type = 1,
            content = languageConvert(tvSourceLanguage.text.toString()) + "/" +
                        languageConvert(tvTargetLanguage.text.toString()),
            extra = recordingFilePath ?: ""
        )

        val items = translationAdapter.getItems().mapIndexed { index, result ->
            TranslateItemEntity(
                orderIndex  = index,
                sourceText  = result.sourceText,
                targetText  = result.targetText
            )
        }

        lifecycleScope.launch {
            db.TranslateHistoryDao().saveFullSession(session, items)
            runOnUiThread {
                ToastUtils.show(this@TranslateRealtimeActivity, "保存成功")
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    // 简单判断字符串最后一个字符是否为常见标点符号
    private fun endsWithPunctuation(text: String): Boolean {
        if (text.isEmpty()) return false
        val punctuations = listOf('.', '。', ',', '，', '!', '！', '?', '？')
        return punctuations.contains(text.last())
    }

    private fun languageConvert(language: String): String {
        return when (language) {
            "中文(简体)" -> "zh"
            "英语" -> "en"
            else -> "zh"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        errorDialog?.dismiss()
        errorDialog = null
        voiceManager.release()
        clasiClient.disconnect()
    }
}