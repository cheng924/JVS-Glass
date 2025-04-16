package com.example.jvsglass.activities.translate

import android.Manifest
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
    private var isRecording = false
    private val executor = Executors.newSingleThreadExecutor()
    private var currentSourceText = ""
    private var currentTargetText = ""

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
        val recyclerView = findViewById<RecyclerView>(R.id.rv_translate_results)
        recyclerView.layoutManager = LinearLayoutManager(this)
        translationAdapter = TranslationAdapter(this, mutableListOf(), languageStyleState)
        recyclerView.adapter = translationAdapter
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

            clasiClient.updateLanguages(
                tvSourceLanguage.text.toString().lowercase(),
                tvTargetLanguage.text.toString().lowercase()
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
                    ivTranslateState.setImageResource(R.drawable.ic_suspend)
                    tvTranslateState.text = "暂停"
                    startAudioRecording()
//                    addTranslationResult("你好", "Hello")
                }
                1 -> {
                    ivTranslateState.setImageResource(R.drawable.ic_continue)
                    tvTranslateState.text = "继续"
                    stopAudioRecording()
//                    addTranslationResult("再见", "Goodbye")
                }
            }
        }
    }

    private fun addTranslationResult(source: String, target: String) {
        translationAdapter.addItem(TranslationResult(source, target))
        findViewById<RecyclerView>(R.id.rv_translate_results).smoothScrollToPosition(translationAdapter.itemCount - 1)
    }

    private fun initClasiClient() {
        clasiClient = RealtimeClasiClient(
            apiKey = BuildConfig.DOUBAO_AI_API_KEY,
            sourceLang = "zh",
            targetLang = "en",
            callback = object : RealtimeClasiClient.ClasiCallback {
                override fun onTranscriptUpdate(text: String) {
                    currentSourceText = text
                    updatePartialDisplay()
                }

                override fun onTranslationUpdate(text: String) {
                    currentTargetText = text
                    updatePartialDisplay()
                }

                override fun onFinalResult(transcript: String, translation: String) {
                    runOnUiThread {
                        addTranslationResult(transcript, translation)
                        resetPartialDisplay()
                    }
                }

                override fun onError(error: String) {
                    runOnUiThread {
                        LogUtils.error(error)
                        stopAudioRecording()
                    }
                }

                override fun onConnectionChanged(connected: Boolean) {
                    runOnUiThread {
                        ivTranslateState.setImageResource(
                            if (connected) R.drawable.ic_suspend else R.drawable.ic_continue
                        )

                        if (!connected) {
                            stopAudioRecording()
                            translateState = 0
                            tvTranslateState.text = "继续"
                        }
                    }
                }

                @RequiresPermission(Manifest.permission.RECORD_AUDIO)
                override fun onSessionReady() {
                    if (translateState == 0) {
                        startAudioRecording()
                    }
                }
            }
        )
        clasiClient.connect()
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    private fun startAudioRecording() {
        if (!clasiClient.isConnected()) {
            LogUtils.error("连接未就绪，请稍后重试")
            translateState = 1
            return
        }

        voiceManager.startRecording(object : VoiceManager.AudioRecordCallback {
            override fun onAudioData(data: ByteArray) {
                // 将音频数据发送到翻译服务
                if (clasiClient.isConnected()) {
                    clasiClient.sendAudioChunk(data)
                } else {
                    runOnUiThread {
                        stopAudioRecording()
                        LogUtils.error("连接已断开")
                    }
                }
            }
        })?.also { audioPath ->
            LogUtils.info("开始录音，路径：$audioPath")
            clasiClient.scheduleConfigUpdate()
        }
    }

    private fun stopAudioRecording() {
        voiceManager.stopRecording()
        clasiClient.commitAudio() // 通知服务器音频结束
        LogUtils.info("停止录音")
    }

    private fun updatePartialDisplay() {
        runOnUiThread {
            when (languageStyleState) {
                0 -> { // 双语显示
                    translationAdapter.updatePartialResult(currentSourceText, currentTargetText)
                }
                1 -> { // 只显示目标语言
                    translationAdapter.updatePartialResult("", currentTargetText)
                }
                2 -> { // 只显示源语言
                    translationAdapter.updatePartialResult(currentSourceText, "")
                }
            }
        }
    }

    private fun resetPartialDisplay() {
        currentSourceText = ""
        currentTargetText = ""
        translationAdapter.updatePartialResult("", "")
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release()
        clasiClient.disconnect()
        executor.shutdown()
    }
}