package com.example.jvsglass.activities.translate

import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_realtime)

        setupUI()
        setupRecyclerView()
        setupButtonStyle()

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

    private fun setupButtonStyle() {
        findViewById<LinearLayout>(R.id.ll_stop).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<ImageView>(R.id.iv_convert).setOnClickListener {
            val language = tvSourceLanguage.text.toString()
            tvSourceLanguage.text = tvTargetLanguage.text
            tvTargetLanguage.text = language
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

                    addTranslationResult("你好", "Hello")
                }
                1 -> {
                    ivTranslateState.setImageResource(R.drawable.ic_continue)
                    tvTranslateState.text = "继续"

                    addTranslationResult("再见", "Goodbye")
                }
            }
        }
    }

    private fun addTranslationResult(source: String, target: String) {
        translationAdapter.addItem(TranslationResult(source, target))
        findViewById<RecyclerView>(R.id.rv_translate_results).smoothScrollToPosition(translationAdapter.itemCount - 1)
    }
}