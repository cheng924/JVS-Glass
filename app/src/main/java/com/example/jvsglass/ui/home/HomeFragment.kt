package com.example.jvsglass.ui.home

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.ui.bt.BluetoothConnectActivity
import com.example.jvsglass.ui.notification.NotificationActivity
import com.example.jvsglass.ui.ai.JVSAIActivity
import com.example.jvsglass.ui.teleprompter.TeleprompterActivity
import com.example.jvsglass.ui.translate.TranslateActivity
import com.example.jvsglass.bluetooth.BluetoothConstants
import com.example.jvsglass.bluetooth.BluetoothConnectManager
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.MyNotificationListenerService
import com.example.jvsglass.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class HomeFragment : Fragment() {

    private var isConnected = false
    private var deviceName = ""
    private var deviceAddress = ""

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        checkPermissions()

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_translate, getString(R.string.translate), TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_teleprompt, getString(R.string.teleprompter), TeleprompterActivity::class.java),
            FunctionItem(R.drawable.ic_ai_normal, getString(R.string.ai_beta), JVSAIActivity::class.java),
            FunctionItem(R.drawable.ic_notification, "消息通知", NotificationActivity::class.java)
//            FunctionItem(R.drawable.ic_transcribe, getString(R.string.transcribe), TranscribeActivity::class.java),
//            FunctionItem(R.drawable.ic_dash_board, getString(R.string.dashboard), DashboardActivity::class.java),
//            FunctionItem(R.drawable.ic_quicknote, getString(R.string.quick_note), QuickNoteActivity::class.java)
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

        view.findViewById<Button>(R.id.btn_bluetooth).setOnClickListener {
            ToastUtils.show(requireContext(), getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, BluetoothActivity::class.java))
        }

        view.findViewById<LinearLayout>(R.id.ll_bluetooth_connect).setOnClickListener {
            val intent = Intent(requireContext(), BluetoothConnectActivity::class.java).apply {
                putExtra("isConnected", isConnected)
                putExtra("deviceName", deviceName)
            }
            startActivity(intent)
        }

        view.findViewById<LinearLayout>(R.id.ll_silent_mode).setOnClickListener {
            ToastUtils.show(requireContext(), getString(R.string.development_tips))
            return@setOnClickListener
        }
    }

    override fun onStart() {
        super.onStart()
        EventBus.getDefault().register(this)
    }

    override fun onStop() {
        super.onStop()
        EventBus.getDefault().unregister(this)
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onConnectionEvent(event: BluetoothConnectManager.ConnectionEvent) {
        isConnected = event.isConnected
        deviceName = event.deviceName.toString()
        deviceAddress = event.deviceAddress.toString()
        LogUtils.debug("[MainActivity] $isConnected $deviceName $deviceAddress")
        val statusText = if (isConnected) "已连接" else "未连接"
        view?.findViewById<TextView>(R.id.tv_bluetooth_status)?.text = statusText

        val statusText2 = if (isConnected) "蓝牙已连接\n${deviceName}" else "蓝牙未连接"
        view?.findViewById<TextView>(R.id.btn_bluetooth)?.text = statusText2
    }

    private fun checkPermissions() {
        val permissions = arrayOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
        if (permissions.any { ActivityCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED }) {
            ActivityCompat.requestPermissions(requireActivity(), permissions, BluetoothConstants.REQUEST_LOCATION)
        }

        if (!isNotificationListenerEnabled()) {
            promptEnableNotificationListener()
        }
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val pkgName = requireContext().packageName
        val flat = Settings.Secure.getString(requireContext().contentResolver, "enabled_notification_listeners")
        return !TextUtils.isEmpty(flat) && flat.contains("$pkgName/${MyNotificationListenerService::class.java.name}")
    }

    private fun promptEnableNotificationListener() {
        ToastUtils.show(requireContext(), "请开启通知访问权限，以使用通知功能")
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        startActivity(intent)
    }
}
