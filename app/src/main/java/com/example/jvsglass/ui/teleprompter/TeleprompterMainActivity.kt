package com.example.jvsglass.ui.teleprompter

import android.Manifest
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.viewModels
import androidx.annotation.RequiresPermission
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.example.jvsglass.R
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.bluetooth.PacketCommandUtils.CMDKey
import com.example.jvsglass.bluetooth.PacketCommandUtils.ENTER_HOME
import com.example.jvsglass.bluetooth.PacketCommandUtils.ENTER_TELEPROMPTER
import com.example.jvsglass.bluetooth.PacketCommandUtils.createPacket
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class TeleprompterMainActivity : AppCompatActivity(), ControlUiCallback {
    private lateinit var fileName: String
    private lateinit var fileDate: String
    private lateinit var fileContent: String

    private lateinit var llVoiceScroll: LinearLayout
    private lateinit var ivMic: ImageView
    private lateinit var tvMic: TextView
    private lateinit var llUniformScroll: LinearLayout
    private lateinit var ivUniform: ImageView
    private lateinit var tvUniform: TextView
    private lateinit var llRemoteScroll: LinearLayout
    private lateinit var ivRemote: ImageView
    private lateinit var tvRemote: TextView

    private val vm: TeleprompterViewModel by viewModels()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_main)

        fileName = intent.getStringExtra("fileName").orEmpty()
        fileDate = intent.getStringExtra("fileDate").orEmpty()
        fileContent = intent.getStringExtra("fileContent").orEmpty()

        BluetoothConnectManager.sendCommand(createPacket(CMDKey.INTERFACE_COMMAND, ENTER_TELEPROMPTER))

        llVoiceScroll = findViewById(R.id.ll_voice_scroll)
        ivMic = findViewById(R.id.iv_mic)
        tvMic = findViewById(R.id.tv_mic)
        llUniformScroll = findViewById(R.id.ll_uniform_scroll)
        ivUniform = findViewById(R.id.iv_uniform)
        tvUniform = findViewById(R.id.tv_uniform)
        llRemoteScroll = findViewById(R.id.ll_remote_scroll)
        ivRemote = findViewById(R.id.iv_remote)
        tvRemote = findViewById(R.id.tv_remote)

        val tabLayout = findViewById<TabLayout>(R.id.tabLayout)
        val viewPager = findViewById<ViewPager2>(R.id.viewPager)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            BluetoothConnectManager.sendCommand(createPacket(CMDKey.INTERFACE_COMMAND, ENTER_HOME))
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<ImageView>(R.id.teleprompter_settings).setOnClickListener {
            vm.sendRequestSettings()
        }

        viewPager.adapter = object: FragmentStateAdapter(this) {
            override fun getItemCount() = 2
            override fun createFragment(pos: Int): Fragment = when (pos) {
                0 -> TeleprompterContentFragment.newInstance(fileName, fileDate, fileContent)
                else -> AiSummaryFragment()
            }
        }

        TabLayoutMediator(tabLayout, viewPager) { tab, pos ->
            tab.text = if (pos == 0) "提词内容" else "AI摘要"
        }.attach()

        llVoiceScroll.setOnClickListener {
            withCurrentFragment {
                toggleVoiceControl()
            }
        }

        llUniformScroll.setOnClickListener {
            withCurrentFragment {
                toggleAutoScroll()
            }
        }

        llRemoteScroll.setOnClickListener {
            withCurrentFragment {
                toggleRemoteControl()
            }
        }
    }

    private fun withCurrentFragment(action: TeleprompterControl.() -> Unit) {
        val pos = findViewById<ViewPager2>(R.id.viewPager).currentItem
        val frag = supportFragmentManager.findFragmentByTag("f$pos")
        if (frag is TeleprompterControl) {
            action(frag)
        }
    }

    private fun updateControlButtons(
        micClickable: Boolean,
        autoClickable: Boolean,
        remoteClickable: Boolean
    ) {
        llVoiceScroll.isClickable = micClickable
        llUniformScroll.isClickable = autoClickable
        llRemoteScroll.isClickable = remoteClickable
    }

    override fun onAutoFinished() {
        tvUniform.text = "匀速滚动"
        ivMic.setImageResource(R.drawable.ic_continue)
        llUniformScroll.setBackgroundResource(R.drawable.rounded_button)
        updateControlButtons(micClickable = false, autoClickable = true, remoteClickable = false)
    }

    override fun onVoiceScrollStarted(isStart: Boolean) {
        if (isStart) {
            tvMic.text = "停止滚动"
            ivMic.setImageResource(R.drawable.ic_mic_off)
            llVoiceScroll.setBackgroundResource(R.drawable.rounded_button_selected)
            updateControlButtons(micClickable = true, autoClickable = false, remoteClickable = false)
        } else {
            tvMic.text = "动态滚动"
            ivMic.setImageResource(R.drawable.ic_mic_on)
            llVoiceScroll.setBackgroundResource(R.drawable.rounded_button)
            updateControlButtons(micClickable = true, autoClickable = true, remoteClickable = true)
        }
    }

    override fun onUniformScrollStarted(isStart: Boolean) {
        if (isStart) {
            tvUniform.text = "停止滚动"
            ivUniform.setImageResource(R.drawable.ic_suspend)
            llUniformScroll.setBackgroundResource(R.drawable.rounded_button_selected)
            updateControlButtons(micClickable = false, autoClickable = true, remoteClickable = false)
        } else {
            tvUniform.text = "匀速滚动"
            ivUniform.setImageResource(R.drawable.ic_continue)
            llUniformScroll.setBackgroundResource(R.drawable.rounded_button)
            updateControlButtons(micClickable = true, autoClickable = true, remoteClickable = true)
        }
    }

    override fun onRemoteScrollStarted(isStart: Boolean) {
        if (isStart) {
            tvRemote.text = "停止遥控"
            ivRemote.setImageResource(R.drawable.ic_remote_off)
            llRemoteScroll.setBackgroundResource(R.drawable.rounded_button_selected)
            updateControlButtons(micClickable = false, autoClickable = false, remoteClickable = true)
        } else {
            tvRemote.text = "遥控滚动"
            ivRemote.setImageResource(R.drawable.ic_remote_on)
            llRemoteScroll.setBackgroundResource(R.drawable.rounded_button)
            updateControlButtons(micClickable = true, autoClickable = true, remoteClickable = true)
        }
    }
}