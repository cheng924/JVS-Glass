package com.example.jvsglass.activities;

import android.os.Bundle;
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.utils.ToastUtils

class TelepromptActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_teleprompt);

        val functions = listOf(
            FileItem("sdhiasigoasjgsfhaogkjksdgjklzxcmisjdkgsdgasdg", "2025/3/4 14:00", "sdhijiasdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsdhijiasgdasjgdsgdasjgd"),
            FileItem("xniujnjnjdnjg", "2025/3/8 15:00", "xniujnjnjdnjg")
        )

        val recyclerView: RecyclerView = findViewById(R.id.rvFiles)
        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.adapter = FileAdapter(functions)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            ToastUtils.show(this, "返回")
            finish() // 关闭当前Activity，回到上一个Activity
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnNew).setOnClickListener {
            ToastUtils.show(this, "新建文本")
        }

        findViewById<LinearLayout>(R.id.btnImport).setOnClickListener {
            ToastUtils.show(this, "导入文本")
        }
    }
}