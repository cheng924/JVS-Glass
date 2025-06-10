package com.example.jvsglass.ui.ai

import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.WebView
import android.widget.ImageView
import android.widget.ScrollView
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils
import java.io.File

class FileViewerActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_file_viewer)

        findViewById<ImageView>(R.id.ivClose).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        // 加载文件内容
        intent.getStringExtra("file_path")?.let { path ->
            when {
                path.endsWith(".txt") -> showText(path)
                else -> handleUnsupportedFormat()
            }
        } ?: run {
            ToastUtils.show(this, "无效文件路径")
            finish()
        }
    }

    private fun showText(path: String) {
        try {
            findViewById<TextView>(R.id.tvFileName).text = path.substringAfterLast('/').substringAfterLast("_")
            findViewById<TextView>(R.id.tvContent).apply {
                visibility = View.VISIBLE
                text = File(path).readText()
            }
            findViewById<ScrollView>(R.id.scrollView).visibility = View.VISIBLE
            findViewById<WebView>(R.id.webView).visibility = View.GONE
        } catch (e: Exception) {
            ToastUtils.show(this, "文件读取失败")
            finish()
        }

    }

    private fun handleUnsupportedFormat() {
        ToastUtils.show(this, "不支持的文件格式")
        finish()
    }
}