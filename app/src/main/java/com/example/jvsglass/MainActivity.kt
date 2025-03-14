package com.example.jvsglass

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
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
import com.example.jvsglass.activities.TeleprompterActivity
import com.example.jvsglass.activities.TranscribeActivity
import com.example.jvsglass.activities.TranslateActivity
import com.example.jvsglass.ui.theme.JVSGlassTheme
import com.example.jvsglass.utils.ToastUtils

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_stub, getString(R.string.quick_note), QuickNoteActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.translate), TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.navigate), NavigateActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.teleprompter), TeleprompterActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.ai_beta), JVSAIActivity::class.java),
            FunctionItem(R.drawable.ic_stub, getString(R.string.transcribe), TranscribeActivity::class.java),
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