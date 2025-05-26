package com.example.jvsglass.activities.teleprompter

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.R
import java.lang.ref.WeakReference

class TeleprompterSettingActivity : AppCompatActivity() {

    object Speed {
        const val HIGH = 10_000L    // 高速
        const val MEDIUM = 15_000L  // 中速
        const val LOW = 20_000L     // 低速
    }

    private val contextRef = WeakReference(this@TeleprompterSettingActivity)
    private var currentSpeed: Long = Speed.MEDIUM
    private var selectedSpeed: Long = Speed.MEDIUM

    private lateinit var rgSpeed: RadioGroup
    private lateinit var tvSave: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_setting)

        initSetting()
        initView()
    }

    private fun initSetting() {
        rgSpeed = findViewById(R.id.rg_speed)

        val intentMs = intent.getLongExtra("scrollIntervalMs", -1L)
        currentSpeed = if (intentMs == Speed.LOW || intentMs == Speed.MEDIUM || intentMs == Speed.HIGH) {
            intentMs
        } else {
            getTeleprompterSpeed() ?: Speed.MEDIUM
        }
        selectedSpeed = currentSpeed

        when (currentSpeed) {
            Speed.LOW -> rgSpeed.check(R.id.rb_10s)
            Speed.HIGH -> rgSpeed.check(R.id.rb_20s)
            else -> rgSpeed.check(R.id.rb_15s)
        }
    }

    private fun initView() {
        findViewById<ImageView>(R.id.iv_back).setOnClickListener {
            setResult(
                Activity.RESULT_OK,
                intent.apply { putExtra("scrollIntervalMs", selectedSpeed) }
            )
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        tvSave = findViewById(R.id.tv_save)
        tvSave.setOnClickListener {
            saveTeleprompterSpeed(selectedSpeed)
            currentSpeed = selectedSpeed
            tvSave.visibility = View.GONE
        }

        rgSpeed.setOnCheckedChangeListener { _, checkedId ->
            val newSpeed = when (checkedId) {
                R.id.rb_10s -> Speed.LOW
                R.id.rb_20s -> Speed.HIGH
                else -> Speed.MEDIUM
            }
            selectedSpeed = newSpeed
            tvSave.visibility = if (selectedSpeed != currentSpeed) View.VISIBLE else View.GONE
        }
    }

    // 存储/读取设备地址
    @SuppressLint("UseKtx")
    private fun saveTeleprompterSpeed(speed: Long) {
        contextRef.get()?.getSharedPreferences("teleprompter_setting", Context.MODE_PRIVATE)?.edit()
            ?.putLong("speed", speed)?.apply()
    }

    private fun getTeleprompterSpeed(): Long? {
        return contextRef.get()?.getSharedPreferences("teleprompter_setting", Context.MODE_PRIVATE)
            ?.getLong("speed", 15_000L)
    }
}