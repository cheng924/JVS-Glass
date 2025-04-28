package com.example.jvsglass.activities.translate

import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.utils.FileHandler
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.utils.VoiceManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranslateHistoryItemActivity :
    AppCompatActivity(),
    FileHandler.FileWriteResultCallback,
    VoiceManager.OnPlaybackCompleteListener
{
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var translationAdapter: TranslationAdapter
    private lateinit var fileHandler: FileHandler
    private lateinit var voiceManager: VoiceManager

    private lateinit var rvTranslateResults: RecyclerView
    private lateinit var llTextSetting: LinearLayout
    private lateinit var tvSourceLanguageSetting: TextView
    private lateinit var tvTargetLanguageSetting: TextView
    private var languageStyleState = 0

    private lateinit var ivPlay: ImageView
    private var audioPath: String? = null
    private enum class PlayState { STOPPED, PLAYING, PAUSED }
    private var playState = PlayState.STOPPED

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_history_item)

        fileHandler = FileHandler(this)
        voiceManager = VoiceManager(this)
        voiceManager.setOnPlaybackCompleteListener(this)
        setupUI()
        initViews()
    }

    private fun setupUI() {
        llTextSetting = findViewById(R.id.ll_text_setting)
        tvSourceLanguageSetting = findViewById(R.id.tv_source_language_setting)
        tvTargetLanguageSetting = findViewById(R.id.tv_target_language_setting)
        ivPlay = findViewById(R.id.ivPlay)
        ivPlay.setImageResource(R.drawable.ic_continue)
        playState = PlayState.STOPPED

        rvTranslateResults = findViewById(R.id.rv_translate_results)
        rvTranslateResults.layoutManager = LinearLayoutManager(this)
        translationAdapter = TranslationAdapter(this, mutableListOf(), languageStyleState)
        rvTranslateResults.adapter = translationAdapter

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        ivPlay.setOnClickListener {
            audioPath?.let { path ->
                when (playState) {
                    PlayState.STOPPED -> {
                        voiceManager.playVoiceMessage(path)
                        ivPlay.setImageResource(R.drawable.ic_suspend)
                        playState = PlayState.PLAYING
                    }
                    PlayState.PLAYING -> {
                        voiceManager.pausePlayback()
                        ivPlay.setImageResource(R.drawable.ic_continue)
                        playState = PlayState.PAUSED
                    }
                    PlayState.PAUSED -> {
                        voiceManager.playVoiceMessage(path)
                        ivPlay.setImageResource(R.drawable.ic_suspend)
                        playState = PlayState.PLAYING
                    }
                }
            } ?: run {
                ToastUtils.show(this, "无可播放录音")
            }
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

        findViewById<LinearLayout>(R.id.ll_export).setOnClickListener {
            if (translationAdapter.getItems().isEmpty()) {
                ToastUtils.show(this@TranslateHistoryItemActivity, "无文本导出")
                return@setOnClickListener
            }
            fileHandler.saveFile(
                "同声传译导出_${SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(Date())}.txt",
                buildExportText(),
                this@TranslateHistoryItemActivity)
        }
    }

    private fun initViews() {
        val sessionId = intent.getLongExtra("session_id", -1L)
        if (sessionId != -1L) {
            lifecycleScope.launch(Dispatchers.IO) {
                val historyWithItems = db.TranslateHistoryDao().getHistoryById(sessionId)
                historyWithItems?.let { items ->
                    withContext(Dispatchers.Main) {
                        audioPath = items.history.extra
                        translationAdapter.clear()
                        items.items.sortedBy { it.orderIndex }.forEach {
                            translationAdapter.addItem(
                                TranslationResult(it.sourceText, it.targetText, false)
                            )
                        }
                    }
                }
            }
        }
    }

    private fun buildExportText(): String {
        return translationAdapter.getItems().joinToString(separator = "\n\n") { result ->
                when (languageStyleState) {
                    0 -> "${result.sourceText}\n${result.targetText}"   // 双语
                    1 -> result.sourceText                              // 仅原文
                    2 -> result.targetText                              // 仅译文
                    else -> "${result.sourceText}\n${result.targetText}"
                }
            }
    }

    override fun onWriteSuccess(savedUri: Uri) {
        LogUtils.info("导出成功：$savedUri")
    }

    override fun onWriteFailure(errorMessage: String) {
        LogUtils.error("导出失败，错误信息：$errorMessage")
    }

    override fun onPlaybackComplete(filePath: String) {
        runOnUiThread {
            ivPlay.setImageResource(R.drawable.ic_continue)
            playState = PlayState.STOPPED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release()
    }
}