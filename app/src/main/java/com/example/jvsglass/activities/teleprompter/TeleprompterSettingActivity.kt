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

    private val contextRef = WeakReference(this@TeleprompterSettingActivity)
    private var currentSpeed: Long = 15_000L
    private var selectedSpeed: Long = 15_000L

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
        currentSpeed = if (intentMs == 10_000L || intentMs == 15_000L || intentMs == 20_000L) {
            intentMs
        } else {
            getTeleprompterSpeed() ?: 15_000L
        }
        selectedSpeed = currentSpeed

        when (currentSpeed) {
            10_000L -> rgSpeed.check(R.id.rb_10s)
            20_000L -> rgSpeed.check(R.id.rb_20s)
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
                R.id.rb_10s -> 10_000L
                R.id.rb_20s -> 20_000L
                else -> 15_000L
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