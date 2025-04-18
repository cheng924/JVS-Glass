package com.example.jvsglass.activities.translate

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.BuildConfig
import com.example.jvsglass.R
import com.example.jvsglass.network.RealtimeClasiClient
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.VoiceManager
import java.util.concurrent.Executors

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

    private lateinit var voiceManager: VoiceManager
    private lateinit var clasiClient: RealtimeClasiClient
    private val executor = Executors.newSingleThreadExecutor()
    private var currentSourceText = ""
    private var currentTargetText = ""

    private var errorDialog: AlertDialog? = null
    private var isManualPause = false
    private var isUpdatingLanguages = false

    private val audioManager by lazy {
        getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_realtime)

        voiceManager = VoiceManager(this)
        setupUI()
        setupRecyclerView()
        setupButtonStyle()
        initClasiClient()
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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun setupButtonStyle() {
        findViewById<LinearLayout>(R.id.ll_stop).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<ImageView>(R.id.iv_convert).setOnClickListener {
            val source = tvSourceLanguage.text.toString()
            val target = tvTargetLanguage.text.toString()

            tvSourceLanguage.text = target
            tvTargetLanguage.text = source

            isUpdatingLanguages = true
            stopAudioRecording()
            clasiClient.updateLanguages(
                languageConvert(tvSourceLanguage.text.toString()),
                languageConvert(tvTargetLanguage.text.toString())
            )
        }

        llTextSetting.setOnClickListener {
            languageStyleState = (languageStyleState + 1) % 3
            when (languageStyleState) {
                0 -> {
                    tvSourceLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.white)) }
                    tvTargetLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.white)) }
                }
                1 -> {
                    tvSourceLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.white)) }
                    tvTargetLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.button_text)) }
                }
                2 -> {
                    tvSourceLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.button_text)) }
                    tvTargetLanguageSetting.apply { setTextColor(ContextCompat.getColor(context, R.color.white)) }
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
                    startAudioRecording()
                }
                1 -> {
                    isManualPause = true
                    ivTranslateState.setImageResource(R.drawable.ic_continue)
                    tvTranslateState.text = "继续"
                    stopAudioRecording()
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
                override fun onTranscriptUpdate(text: String) {
                    runOnUiThread {
                        LogUtils.info("---1--- 收到实时语音识别结果：$text")
                        currentSourceText += text
                        updatePartialDisplay()
                    }
                }

                override fun onTranslationUpdate(text: String) {
                    runOnUiThread {
                        LogUtils.info("---2--- 收到实时语音翻译结果：$text")
                        currentTargetText += text
                        updatePartialDisplay()
                    }
                }

                override fun onFinalResult(transcript: String, translation: String) {
                    runOnUiThread {
                        LogUtils.info("---3--- 收到实时语音识别结果：$transcript, 翻译结果：$translation")
                        addTranslationResult(transcript, translation)
                        currentSourceText = ""
                        currentTargetText = ""
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        if (!isManualPause && !isUpdatingLanguages) {
                            LogUtils.info("连接失败，停止录音")
                            if (errorDialog?.isShowing != true) {
                                errorDialog = AlertDialog.Builder(this@TranslateRealtimeActivity)
                                    .setMessage("网络波动，请稍后")
                                    .setCancelable(false) // 禁止点击外部关闭
                                    .create()
                                errorDialog?.show()
                            }
                            LogUtils.error(error)
                            stopAudioRecording()
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

                        translateState = 0
                        ivTranslateState.setImageResource(R.drawable.ic_suspend)
                        tvTranslateState.text = "暂停"
                        isUpdatingLanguages = false
                        if (!isManualPause) {
                            startAudioRecording()
                        }
                    }
                }
            }
        )
        clasiClient.connect()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioRecording() {
        if (!isBluetoothHeadsetConnected()) {
            AlertDialog.Builder(this)
                .setTitle("蓝牙未连接")
                .setMessage("请连接蓝牙耳机后重试")
                .setPositiveButton("确定", null)
                .show()
            return
        }

        if (!clasiClient.isConnected()) {
            LogUtils.error("连接未就绪，请稍后重试")
            translateState = 1
            return
        }

        voiceManager.startStreaming(object : VoiceManager.AudioRecordCallback {
            override fun onAudioData(data: ByteArray) {
                // 将音频数据发送到翻译服务
                if (clasiClient.isConnected()) {
                    clasiClient.sendAudioChunk(data)
                }
            }
        })
        LogUtils.info("实时语音采集已启动")
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        return audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any {
            it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
        }
    }

    private fun stopAudioRecording() {
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
    private fun updatePartialDisplay() {
        translationAdapter.updatePartialPair(currentSourceText, currentTargetText)
        // 检查当前累积的文本是否都以标点结束
        if (endsWithPunctuation(currentSourceText) && endsWithPunctuation(currentTargetText)) {
            currentSourceText = ""
            currentTargetText = ""
        }
        rvTranslateResults.post {
            rvTranslateResults.smoothScrollToPosition(translationAdapter.itemCount - 1)
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
            "CN" -> "zh"
            "EN" -> "en"
            else -> "zh"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        errorDialog?.dismiss()
        errorDialog = null
        voiceManager.release()
        clasiClient.disconnect()
        executor.shutdown()
    }
}