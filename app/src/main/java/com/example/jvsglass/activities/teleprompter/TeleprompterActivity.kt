package com.example.jvsglass.activities.teleprompter

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.toFileItem
import com.example.jvsglass.utils.ToastUtils
import kotlinx.coroutines.launch

class TeleprompterActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private val adapter = FileAdapter()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter)

        val recyclerView: RecyclerView = findViewById(R.id.rvFiles)
        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.adapter = adapter

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish() // 关闭当前Activity，回到上一个Activity
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnNew).setOnClickListener {
            startActivity(Intent(this, TeleprompterNewFileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnImport).setOnClickListener {
            ToastUtils.show(this, "导入文本")
        }

        // 实时数据监听
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.TeleprompterArticleDao().getAll().collect { articles ->
                    val fileItems = articles.map { it.toFileItem() }
                    adapter.submitList(fileItems)
                }
            }
        }
    }
}