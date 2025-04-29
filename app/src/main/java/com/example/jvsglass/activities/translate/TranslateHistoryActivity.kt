package com.example.jvsglass.activities.translate

import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.TranslateHistoryEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TranslateHistoryActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var translateHistoryAdapter: TranslateHistoryAdapter
    private var translateType: Int = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_history)

        translateType = intent.getIntExtra("translate_type", -1)
        findViewById<TextView>(R.id.tv_title).text = when (translateType) {
            1 -> { "同声传译 - 历史记录" }
            2 -> { "文档翻译 - 历史记录" }
            else -> { "同声传译 - 历史记录" }
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.rv_translate_history)
        recyclerView.layoutManager = LinearLayoutManager(this)
        translateHistoryAdapter = TranslateHistoryAdapter()
        recyclerView.adapter = translateHistoryAdapter

        translateHistoryAdapter.setOnItemClickListener(object : TranslateHistoryAdapter.OnItemClickListener {
            override fun onItemClick(session: TranslateHistoryEntity) {
                val intent = Intent(this@TranslateHistoryActivity,
                    TranslateHistoryItemActivity::class.java).apply {
                    putExtra("session_id", session.id)
                }
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            }
        })

        lifecycleScope.launch(Dispatchers.IO) {
            val listWithItems = if (translateType == 1 || translateType == 2) {
                db.TranslateHistoryDao().getHistoriesByType(translateType)
            } else {
                db.TranslateHistoryDao().getAllHistoriesWithItems()
            }
            val sessions = listWithItems.map { it.history }
            withContext(Dispatchers.Main) {
                translateHistoryAdapter.setData(sessions)
            }
        }
    }
}