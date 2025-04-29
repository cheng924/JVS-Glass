package com.example.jvsglass.activities.translate

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R

class TranslateActivity : AppCompatActivity() {
    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.ll_realtime_translate).setOnClickListener {
            startActivity(Intent(this, TranslateRealtimeActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.ll_conversation_translate).setOnClickListener {
            startActivity(Intent(this, TranslateExchangeActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.ll_document_translate).setOnClickListener {
            startActivity(Intent(this, TranslateFileActivity::class.java))
        }

        findViewById<TextView>(R.id.tv_realtime_history).setOnClickListener {
            val intent = Intent(this, TranslateHistoryActivity::class.java).apply {
                putExtra("translate_type", 1)
            }
            startActivity(intent)
        }

        findViewById<TextView>(R.id.tv_document_history).setOnClickListener {
            val intent = Intent(this, TranslateHistoryActivity::class.java).apply {
                putExtra("translate_type", 2)
            }
            startActivity(intent)
        }
    }
}