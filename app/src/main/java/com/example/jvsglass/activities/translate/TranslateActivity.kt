package com.example.jvsglass.activities.translate

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R

class TranslateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate)

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
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
    }
}