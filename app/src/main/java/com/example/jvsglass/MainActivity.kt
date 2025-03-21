package com.example.jvsglass

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.activities.BluetoothConnectActivity
import com.example.jvsglass.activities.DashboardActivity
import com.example.jvsglass.activities.JVSAIActivity
import com.example.jvsglass.activities.NavigateActivity
import com.example.jvsglass.activities.QuickNoteActivity
import com.example.jvsglass.activities.teleprompter.TeleprompterActivity
import com.example.jvsglass.activities.TranscribeActivity
import com.example.jvsglass.activities.TranslateActivity
import com.example.jvsglass.ble.HeartbeatDetectorManager
import com.example.jvsglass.ui.theme.JVSGlassTheme
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import org.greenrobot.eventbus.EventBus
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_quicknote, getString(R.string.quick_note), QuickNoteActivity::class.java),
            FunctionItem(R.drawable.ic_translate, getString(R.string.translate), TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_navigate, getString(R.string.navigate), NavigateActivity::class.java),
            FunctionItem(R.drawable.ic_teleprompt, getString(R.string.teleprompter), TeleprompterActivity::class.java),
            FunctionItem(R.drawable.ic_ai, getString(R.string.ai_beta), JVSAIActivity::class.java),
            FunctionItem(R.drawable.ic_transcribe, getString(R.string.transcribe), TranscribeActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.dashboard), DashboardActivity::class.java),
        )

        val recyclerView: RecyclerView = findViewById(R.id.rvFunctions)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = FunctionAdapter(functions)

        // 设置按钮点击
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            ToastUtils.show(this, getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnBluetooth).setOnClickListener {
            ToastUtils.show(this, getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, BluetoothActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.bluetoothConnect).setOnClickListener {
//            ToastUtils.show(this, getString(R.string.development_tips))
//            return@setOnClickListener
            startActivity(Intent(this, BluetoothConnectActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.silentMode).setOnClickListener {
            ToastUtils.show(this, getString(R.string.development_tips))
            return@setOnClickListener
//            startActivity(Intent(this, SilentModeActivity::class.java))
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

    @Subscribe(threadMode = ThreadMode.MAIN, sticky = true)
    fun onConnectionEvent(event: HeartbeatDetectorManager.ConnectionEvent) {
        LogUtils.info("Event received: ${event.isConnected}")
        val statusText = if (event.isConnected) "Connected" else "Disconnected"
        findViewById<TextView>(R.id.tvBluetoothStatus).text = statusText
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    JVSGlassTheme {
        Greeting("Android")
    }
}