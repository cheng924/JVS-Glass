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
import com.example.jvsglass.activities.BluetoothActivity
import com.example.jvsglass.activities.DashboardActivity
import com.example.jvsglass.activities.DisconnectedActivity
import com.example.jvsglass.activities.JVSAIActivity
import com.example.jvsglass.activities.NavigateActivity
import com.example.jvsglass.activities.QuickNoteActivity
import com.example.jvsglass.activities.SettingsActivity
import com.example.jvsglass.activities.SilentModeActivity
import com.example.jvsglass.activities.TelepromptActivity
import com.example.jvsglass.activities.TranscribeActivity
import com.example.jvsglass.activities.TranslateActivity
import com.example.jvsglass.ui.theme.JVSGlassTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 初始化功能列表
        val functions = listOf(
            FunctionItem(R.drawable.ic_stub, "QuickNote", QuickNoteActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "Translate", TranslateActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "Navigate", NavigateActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "Teleprompt", TelepromptActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "JVS AI(beta)", JVSAIActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "Transcribe", TranscribeActivity::class.java),
            FunctionItem(R.drawable.ic_stub, "Dashboard", DashboardActivity::class.java),
        )

        val recyclerView: RecyclerView = findViewById(R.id.rvFunctions)
        recyclerView.layoutManager = GridLayoutManager(this, 2)
        recyclerView.adapter = FunctionAdapter(functions)

        // 设置按钮点击
        findViewById<ImageView>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        findViewById<Button>(R.id.btnBluetooth).setOnClickListener {
            startActivity(Intent(this, BluetoothActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.disconnected).setOnClickListener {
            startActivity(Intent(this, DisconnectedActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.silentMode).setOnClickListener {
            startActivity(Intent(this, SilentModeActivity::class.java))
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