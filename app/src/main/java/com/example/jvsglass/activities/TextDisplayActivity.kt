package com.example.jvsglass.activities;

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.method.ScrollingMovementMethod
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils

class TextDisplayActivity : AppCompatActivity() {

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState : Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_text_display)

        // 获取传递数据
        val filename = intent.getStringExtra("filename") ?: ""
        val filedate = intent.getStringExtra("filedate") ?: ""
        val filecontent = intent.getStringExtra("filecontent") ?: ""

        // 初始化视图
        findViewById<TextView>(R.id.tvTitle).text = filename
        findViewById<TextView>(R.id.tvDate).text = filedate
        findViewById<TextView>(R.id.tvContent).apply {
            text = filecontent
            // 大文本优化
            movementMethod = ScrollingMovementMethod()
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            ToastUtils.show(this, "返回")
            finish() // 关闭当前Activity，回到上一个Activity
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnSettings).setOnClickListener {
            ToastUtils.show(this, "设置文本")
        }

        findViewById<LinearLayout>(R.id.btnStart).setOnClickListener {
            ToastUtils.show(this, "开始")
        }
    }
}