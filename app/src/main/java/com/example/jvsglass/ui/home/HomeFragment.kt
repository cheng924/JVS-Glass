package com.example.jvsglass.ui.home

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.ui.bt.BluetoothConnectActivity
import com.example.jvsglass.ui.notification.NotificationActivity
import com.example.jvsglass.ui.teleprompter.TeleprompterActivity
import com.example.jvsglass.ui.translate.TranslateActivity
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.ui.meeting.MeetingAssistantActivity
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HomeFragment : Fragment() {

    private var isConnected = false
    private var deviceName = ""
    private var deviceAddress = ""

    private val btConnectLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == AppCompatActivity.RESULT_OK) {
            val connected = result.data?.getStringExtra("CONNECTED_DEVICE")
            if (!connected.isNullOrBlank()) {
                isConnected = true
                deviceName = connected
            } else {
                isConnected = false
                deviceName = ""
            }
        } else {
            isConnected = false
            deviceName = ""
        }
        updateBluetoothStatus()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        LogUtils.info("----- Home Fragment Created -----")

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_translate, getString(R.string.translate), "实时翻译", TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_teleprompt, getString(R.string.teleprompter), "智能提醒", TeleprompterActivity::class.java),
            FunctionItem(R.drawable.ic_meeting_assistant, "会议助手", "远程协作", MeetingAssistantActivity::class.java),
            FunctionItem(R.drawable.ic_notification, "智慧提醒", "N条未读", NotificationActivity::class.java)
        )

        val recyclerView: RecyclerView = view.findViewById(R.id.rv_functions)
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        recyclerView.adapter = FunctionAdapter(functions)

        // 设置按钮点击
        view.findViewById<ImageView>(R.id.iv_settings).setOnClickListener {
            ToastUtils.show(requireContext(), getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, SettingsActivity::class.java))
        }

        view.findViewById<LinearLayout>(R.id.ll_bluetooth_connect).setOnClickListener {
            val intent = Intent(requireContext(), BluetoothConnectActivity::class.java).apply {
                putExtra("isConnected", isConnected)
                putExtra("deviceName", deviceName)
            }
            btConnectLauncher.launch(intent)
        }

        updateBluetoothStatus()
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    private fun updateBluetoothStatus() {
        LogUtils.info("[HomeFragment] 连接状态：$isConnected $deviceName $deviceAddress")
        val statusText1 = if (isConnected) "已连接" else "未连接"
        view?.findViewById<TextView>(R.id.tv_bluetooth_status)?.text = statusText1
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectionEvent(event: BluetoothConnectManager.ConnectionEvent) {
        isConnected = event.isConnected
        deviceName = event.deviceName.toString()
        deviceAddress = event.deviceAddress.toString()
        LogUtils.debug("[HomeFragment] $isConnected $deviceName $deviceAddress")
        val statusText = if (isConnected) "已连接" else "未连接"
        view?.findViewById<TextView>(R.id.tv_bluetooth_status)?.text = statusText
    }
}
