package com.example.jvsglass.activities.translate

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
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
import java.io.File

class TranslateHistoryActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var translateHistoryAdapter: TranslateHistoryAdapter
    private var translateType: Int = 1

    private lateinit var rlTotalHistoryBar: RelativeLayout
    private lateinit var tvTotalHistories: TextView
    private lateinit var rlDeleteHistoryBar: RelativeLayout
    private lateinit var tvDeleteHistories: TextView
    private lateinit var tvDelete: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_translate_history)
        
        initView()
        getHistoryData()
    }

    private fun initView() {
        rlTotalHistoryBar = findViewById(R.id.rl_total_history_bar)
        tvTotalHistories = findViewById(R.id.tv_total_histories)
        rlDeleteHistoryBar = findViewById(R.id.rl_delete_history_bar)
        tvDeleteHistories = findViewById(R.id.tv_delete_histories)
        tvDelete = findViewById(R.id.tv_delete)

        translateType = intent.getIntExtra("translate_type", -1)
        findViewById<TextView>(R.id.tv_title).text = when (translateType) {
            1 -> "同声传译 - 历史记录"
            2 -> "文档翻译 - 历史记录"
            else -> "同声传译 - 历史记录"
        }

        findViewById<ImageView>(R.id.ivBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        val recyclerView = findViewById<RecyclerView>(R.id.rv_translate_history)
        recyclerView.layoutManager = LinearLayoutManager(this)
        translateHistoryAdapter = TranslateHistoryAdapter(
            onItemClick = { session ->
                val intent = Intent(this, TranslateHistoryItemActivity::class.java)
                intent.putExtra("session_id", session.id)
                startActivity(intent)
                overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
            },
            onSelectionCountChanged = { count ->
                if (count > 0) {
                    rlTotalHistoryBar.visibility = View.GONE
                    rlDeleteHistoryBar.visibility = View.VISIBLE
                    tvDelete.visibility = View.VISIBLE
                    tvDeleteHistories.text = count.toString()
                } else {
                    rlTotalHistoryBar.visibility = View.VISIBLE
                    rlDeleteHistoryBar.visibility = View.GONE
                    tvDelete.visibility = View.GONE
                }
            },
            onDeleteClick = { session ->
                AlertDialog.Builder(this@TranslateHistoryActivity)
                    .setTitle("确认删除")
                    .setMessage("是否删除此条记录？")
                    .setPositiveButton("删除") { _, _ -> deleteHistory(listOf(session)) }
                    .setNegativeButton("取消", null)
                    .show()
            }
        )
        recyclerView.adapter = translateHistoryAdapter

        // 批量删除按钮
        tvDelete.setOnClickListener {
            val selected = translateHistoryAdapter.getSelectedItems()
            if (selected.isEmpty()) return@setOnClickListener

            AlertDialog.Builder(this)
                .setTitle("确认删除")
                .setMessage("是否删除选中的 ${selected.size} 条记录？")
                .setPositiveButton("删除") { _, _ -> deleteHistory(selected) }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun getHistoryData() {
        lifecycleScope.launch(Dispatchers.IO) {
            val listWithItems = if (translateType == 1 || translateType == 2) {
                db.TranslateHistoryDao().getHistoriesByType(translateType)
            } else {
                db.TranslateHistoryDao().getAllHistoriesWithItems()
            }
            val sessions = listWithItems.map { it.history }
            withContext(Dispatchers.Main) {
                translateHistoryAdapter.setData(sessions)
                rlTotalHistoryBar.visibility = View.VISIBLE
                tvTotalHistories.text = sessions.size.toString()
                rlDeleteHistoryBar.visibility = View.GONE
            }
        }
    }

    private fun deleteHistory(selected: List<TranslateHistoryEntity>) {
        lifecycleScope.launch(Dispatchers.IO) {
            selected.forEach { session ->
                db.TranslateHistoryDao().deleteHistory(session.id)
                if (session.type == 1) {
                    session.extra.let { path ->
                        val file = File(path)
                        if (file.exists()) file.delete()
                    }
                }
            }
            withContext(Dispatchers.Main) {
                translateHistoryAdapter.exitSelectionMode()
                getHistoryData()
            }
        }
    }
}