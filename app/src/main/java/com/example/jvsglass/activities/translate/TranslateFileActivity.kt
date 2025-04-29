package com.example.jvsglass.activities.translate

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.TranslateHistoryEntity
import com.example.jvsglass.database.TranslateItemEntity
import com.example.jvsglass.dialog.LanguagePickerDialog
import com.example.jvsglass.dialog.WaitingDialog
import com.example.jvsglass.utils.FileHandler
import com.example.jvsglass.network.ChatRequest
import com.example.jvsglass.network.ChatResponse
import com.example.jvsglass.network.NetworkManager
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import com.example.jvsglass.dialog.WarningDialog
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class TranslateFileActivity :
    AppCompatActivity(),
    FileHandler.FileReadResultCallback,
    FileHandler.FileWriteResultCallback
{
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var translationAdapter: TranslationAdapter

    private lateinit var tvLanguagePicker: TextView
    private lateinit var llImport: LinearLayout
    private lateinit var llExport: LinearLayout

    private lateinit var fileHandler: FileHandler
    private lateinit var tvTranslateTitle: TextView
    private lateinit var rvTranslateResults: RecyclerView

    private lateinit var llTextSetting: LinearLayout
    private lateinit var tvSourceLanguageSetting: TextView
    private lateinit var tvTargetLanguageSetting: TextView
    private var languageStyleState = 0

    private var currentLanguage = "英语"
    private var translateContent: String = ""
    private var filename: String = ""
    private var timestamp: Long = 0L

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_file)

        fileHandler = FileHandler(this)
        setupUI()
        setupRecyclerView()
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private fun setupUI() {
        tvLanguagePicker = findViewById(R.id.tv_language_picker)
        llImport = findViewById(R.id.ll_import)
        llExport = findViewById(R.id.ll_export)
        tvTranslateTitle = findViewById(R.id.tv_translate_title)
        rvTranslateResults = findViewById(R.id.rv_translate_results)

        llTextSetting = findViewById(R.id.ll_text_setting)
        tvSourceLanguageSetting = findViewById(R.id.tv_source_language_setting)
        tvTargetLanguageSetting = findViewById(R.id.tv_target_language_setting)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            if (translationAdapter.getItems().isNotEmpty()) {
                WarningDialog.showDialog(
                    context = this@TranslateFileActivity,
                    title = "数据存储提示",
                    message = "本次翻译是否需要记录？",
                    positiveButtonText = "保存",
                    negativeButtonText = "取消",
                    listener = object : WarningDialog.DialogButtonClickListener {
                        override fun onPositiveButtonClick() {
                            saveHistory()
                        }

                        override fun onNegativeButtonClick() {
                            LogUtils.info("[TranslateFileActivity] 取消保存")
                            finish()
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                        }
                    })
            } else {
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }

        tvLanguagePicker.setOnClickListener {
            showLanguagePickerDialog()
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

        llImport.setOnClickListener {
            WarningDialog.showDialog(
                context = this@TranslateFileActivity,
                title = "文件格式提示",
                message = "当前仅支持导入txt格式文本",
                positiveButtonText = "确定",
                negativeButtonText = "取消",
                listener = object : WarningDialog.DialogButtonClickListener {
                    override fun onPositiveButtonClick() {
                        fileHandler.openFilePicker(this@TranslateFileActivity)
                    }

                    override fun onNegativeButtonClick() { }
            })
        }

        llExport.setOnClickListener {
            if (translateContent.isEmpty()) {
                ToastUtils.show(this@TranslateFileActivity, "无文本导出")
                return@setOnClickListener
            }
            fileHandler.saveFile(
                "翻译导出_${SimpleDateFormat("MMdd_HHmmss", Locale.getDefault()).format(Date())}.txt",
                exportFile(translateContent, languageStyleState),
                this@TranslateFileActivity)
        }
    }

    private fun setupRecyclerView() {
        rvTranslateResults = findViewById(R.id.rv_translate_results)
        rvTranslateResults.layoutManager = LinearLayoutManager(this)
        translationAdapter = TranslationAdapter(this, mutableListOf(), languageStyleState)
        rvTranslateResults.adapter = translationAdapter
    }

    override fun onReadSuccess(name: String, content: String) {
        filename = name
        timestamp = System.currentTimeMillis()
//        translateTitle(name, tvLanguagePicker.text.toString())
        sendTextToTranslateModel(content)
    }

    override fun onReadFailure(errorMessage: String) {
        LogUtils.error("错误: $errorMessage")
    }

    override fun onWriteSuccess(savedUri: Uri) {
        LogUtils.info("导出成功：$savedUri")
    }

    override fun onWriteFailure(errorMessage: String) {
        LogUtils.error("导出失败，错误信息：$errorMessage")
    }

    private fun translateTitle(filename: String, targetLanguage: String) {
        val titlePrompt = "$filename，将以上内容翻译成${targetLanguage}"
        NetworkManager.getInstance().chatTextCompletion(
            messages = listOf(ChatRequest.Message(role = "user", content = titlePrompt)),
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    val title = result.choices.firstOrNull()?.message?.content?.trim()
                    runOnUiThread {
                        if (!title.isNullOrEmpty()) {
                            tvTranslateTitle.text = title
                        }
                    }
                }
                override fun onFailure(error: Throwable) {
                    LogUtils.error("生成标题失败：${error.message}")
                }
            }
        )
    }

    private fun sendTextToTranslateModel(textContent: String) {
        val waitingDialog = WaitingDialog.show(this@TranslateFileActivity)
        waitingDialog.setMessage("翻译中，请稍候...")

        val chatMessages = mutableListOf<ChatRequest.Message>()
        val userMsg = ChatRequest.Message(
            role = "user",
            content = getTranslationInstruction(textContent, tvLanguagePicker.text.toString())
        )
        chatMessages.add(userMsg)

        NetworkManager.getInstance().uploadFileTextCompletion(
            messages = chatMessages,
            temperature = 0.7,
            object : NetworkManager.ModelCallback<ChatResponse> {
                override fun onSuccess(result: ChatResponse) {
                    waitingDialog.dismiss()
                    result.choices.firstOrNull()?.let { choice ->
                        val aiContent = choice.message.content
                        chatMessages.add(
                            ChatRequest.Message(
                            role = choice.message.role,
                            content = aiContent
                        ))
                        translateContent = aiContent
                        LogUtils.info(aiContent)
                        parseText(aiContent)
                    }
                }

                override fun onFailure(error: Throwable) {
                    waitingDialog.dismiss()
                    LogUtils.error("请求失败: ${error.message?.substringBefore("\n") ?: "未知错误"}")
                }
            })
    }

    private fun parseText(aiContent: String) {
        aiContent
            .split(Regex("\\r?\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { block ->
                val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val source = lines.firstOrNull() ?: ""
                val target = lines.drop(1).joinToString("")
                translationAdapter.updatePartialPair(source, target)
            }
    }

    private fun exportFile(content: String, languageStyleState: Int): String {
        val stringBuilder = StringBuilder()
        content
            .split(Regex("\\r?\\n{2,}"))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .forEach { block ->
                val lines = block.lines().map { it.trim() }.filter { it.isNotEmpty() }
                val source = lines.firstOrNull() ?: ""
                val target = lines.drop(1).joinToString("")

                when (languageStyleState) {
                    0 -> { // 双语：原文 + 换行 + 译文
                        stringBuilder.append(source)
                        stringBuilder.append("\n")
                        stringBuilder.append(target)
                    }
                    1 -> { // 仅原文
                        stringBuilder.append(source)
                    }
                    2 -> { // 仅译文
                        stringBuilder.append(target)
                    }
                }
                stringBuilder.append("\n\n")
            }
        return stringBuilder.toString().trimEnd()
    }

    private fun saveHistory() {
        val session = TranslateHistoryEntity(
            timestamp = timestamp,
            type = 2,
            content = "源语言/$currentLanguage",
            extra = filename
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
                ToastUtils.show(this@TranslateFileActivity, "保存成功")
                finish()
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        }
    }

    private fun getTranslationInstruction(content: String, targetLanguage: String): String {
        return """
            $content
            将以上内容都翻译成${targetLanguage}，一句原文一句译文，以一个换行符分段。
            请严格按照原文段落格式输出，不要改变句式及段落结构。
        """.trimIndent()
    }

    private fun showLanguagePickerDialog() {
        val dialog = LanguagePickerDialog(
            context = this,
            currentLanguage = currentLanguage,
            onConfirm = { selectedLanguage ->
                currentLanguage = selectedLanguage
                tvLanguagePicker.text = selectedLanguage
                tvTranslateTitle.text = ""
                translationAdapter.clear()
            }
        )
        dialog.show()
    }
}