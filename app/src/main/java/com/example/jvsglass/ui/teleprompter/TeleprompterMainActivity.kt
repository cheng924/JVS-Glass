package com.example.jvsglass.ui.teleprompter

import android.Manifest
import android.os.Bundle
import android.widget.ImageView
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

class TeleprompterMainActivity : AppCompatActivity() {
    private lateinit var fileName: String
    private lateinit var fileDate: String
    private lateinit var fileContent: String

    private val vm: TeleprompterViewModel by viewModels()

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter_main)

        fileName = intent.getStringExtra("fileName").orEmpty()
        fileDate = intent.getStringExtra("fileDate").orEmpty()
        fileContent = intent.getStringExtra("fileContent").orEmpty()

        BluetoothConnectManager.sendCommand(createPacket(CMDKey.INTERFACE_COMMAND, ENTER_TELEPROMPTER))

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
    }
}