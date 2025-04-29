package com.example.jvsglass.activities.translate

import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
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

    private lateinit var tvTitle: TextView
    private lateinit var llVoiceControl: LinearLayout
    private lateinit var seekBar: SeekBar
    private lateinit var tvCurrentTime: TextView
    private lateinit var tvTotalDuration: TextView
    private lateinit var ivPlay: ImageView
    private lateinit var rvTranslateResults: RecyclerView
    private lateinit var llTextSetting: LinearLayout
    private lateinit var tvSourceLanguageSetting: TextView
    private lateinit var tvTargetLanguageSetting: TextView
    private var languageStyleState = 0

    private var timestamp: Long = 0L
    private var type: Int = 1
    private var audioPath: String? = null
    private enum class PlayState { STOPPED, PLAYING, PAUSED }
    private var playState = PlayState.STOPPED
    private val uiHandler = Handler(Looper.getMainLooper())
    private val progressUpdater = object : Runnable {
        override fun run() {
            audioPath?.let {
                // 更新进度
                val pos = voiceManager.getCurrentPosition()
                seekBar.progress = pos
                tvCurrentTime.text = formatTime(pos)
                uiHandler.postDelayed(this, 500)
            }
        }
    }

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
        tvTitle = findViewById(R.id.tv_title)
        llVoiceControl = findViewById(R.id.ll_voice_control)
        tvCurrentTime = findViewById(R.id.tv_current_time)
        tvTotalDuration = findViewById(R.id.tv_total_duration)
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
                        val duration = voiceManager.getDuration()
                        seekBar.max = duration
                        tvTotalDuration.text = formatTime(duration)
                        tvCurrentTime.text = formatTime(0)
                        uiHandler.post(progressUpdater)
                        ivPlay.setImageResource(R.drawable.ic_suspend)
                        playState = PlayState.PLAYING
                    }
                    PlayState.PLAYING -> {
                        voiceManager.pausePlayback()
                        uiHandler.removeCallbacks(progressUpdater)
                        ivPlay.setImageResource(R.drawable.ic_continue)
                        playState = PlayState.PAUSED
                    }
                    PlayState.PAUSED -> {
                        voiceManager.playVoiceMessage(path)
                        uiHandler.post(progressUpdater)
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

        findViewById<LinearLayout>(R.id.ll_export).setOnClickListener {
            if (translationAdapter.getItems().isEmpty()) {
                ToastUtils.show(this@TranslateHistoryItemActivity, "无文本导出")
                return@setOnClickListener
            }
            val fileType = if (type == 1) "同声传译" else "文本翻译"
            fileHandler.saveFile(
                "${fileType}导出_${SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(timestamp)}.txt",
                buildExportText(),
                this@TranslateHistoryItemActivity)
        }

        seekBar = findViewById(R.id.seekBar)
        seekBar.setOnSeekBarChangeListener(object: SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    voiceManager.seekTo(progress)
                    tvCurrentTime.text = formatTime(progress)
                }
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })
    }

    private fun initViews() {
        val sessionId = intent.getLongExtra("session_id", -1L)
        if (sessionId != -1L) {
            lifecycleScope.launch(Dispatchers.IO) {
                val historyWithItems = db.TranslateHistoryDao().getHistoryById(sessionId)
                historyWithItems?.let { items ->
                    withContext(Dispatchers.Main) {
                        timestamp = items.history.timestamp
                        type = items.history.type
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
            uiHandler.removeCallbacks(progressUpdater)
            seekBar.progress = 0
            tvCurrentTime.text = formatTime(0)
            ivPlay.setImageResource(R.drawable.ic_continue)
            playState = PlayState.STOPPED
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        voiceManager.release()
    }

    private fun formatTime(milliseconds: Int): String {
        val totalSeconds = milliseconds / 1000
        val minutes = totalSeconds / 60
        val seconds = totalSeconds % 60
        return String.format(Locale.getDefault(), "%02d : %02d", minutes, seconds)
    }
}