package com.example.jvsglass.activities.teleprompter

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.TeleprompterArticle
import com.example.jvsglass.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TeleprompterNewFileActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_new_file)

        findViewById<TextView>(R.id.tv_date).text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<TextView>(R.id.tv_save_file).setOnClickListener {
            if (findViewById<EditText>(R.id.et_content).text.toString().trim().isNotEmpty()) {
                ToastUtils.show(this, "保存文本")
                saveToDatabase()
            } else {
                ToastUtils.show(this, "内容不能为空")
            }
        }
    }

    private fun saveToDatabase() {
        val article = TeleprompterArticle(
            title = findViewById<TextView>(R.id.et_title).text.toString().ifEmpty { "Untitled" },
            createDate = findViewById<TextView>(R.id.tv_date).text.toString(),
            content = findViewById<TextView>(R.id.et_content).text.toString()
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.TeleprompterArticleDao().insert(article)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@TeleprompterNewFileActivity, "保存成功")
                    finishWithAnimation()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@TeleprompterNewFileActivity, "保存失败：${e.message}")
                }
            }
        }
    }

    private fun finishWithAnimation() {
        setResult(Activity.RESULT_OK)
        finish()
        overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
    }
}