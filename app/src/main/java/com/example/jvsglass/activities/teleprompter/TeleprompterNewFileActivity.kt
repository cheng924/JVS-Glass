package com.example.jvsglass.activities.teleprompter

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Bundle
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

        findViewById<TextView>(R.id.tvDate).text = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/M/dd HH:mm"))
        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<TextView>(R.id.btnSaveFile).setOnClickListener {
            ToastUtils.show(this, "保存文本")
            saveToDatabase()
        }
    }

    private fun saveToDatabase() {
        val article = TeleprompterArticle(
            title = findViewById<TextView>(R.id.etTitle).text.toString().ifEmpty { "Untitled" },
            createDate = findViewById<TextView>(R.id.tvDate).text.toString(),
            content = findViewById<TextView>(R.id.etContent).text.toString()
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
        overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
    }
}