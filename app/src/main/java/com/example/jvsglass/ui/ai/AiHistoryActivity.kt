package com.example.jvsglass.ui.ai

import android.app.AlertDialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AiConversationEntity
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class AiHistoryActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var adapter: AiHistoryAdapter

    private lateinit var rlTotalHistoryBar: RelativeLayout
    private lateinit var tvTotalHistories: TextView
    private lateinit var rlDeleteHistoryBar: RelativeLayout
    private lateinit var tvDeleteHistories: TextView
    private lateinit var rvHistoryList: RecyclerView
    private lateinit var tvDelete: TextView

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_history)

        rlTotalHistoryBar = findViewById(R.id.rl_total_history_bar)
        tvTotalHistories = findViewById(R.id.tv_total_histories)
        rlDeleteHistoryBar = findViewById(R.id.rl_delete_history_bar)
        tvDeleteHistories = findViewById(R.id.tv_delete_histories)
        rvHistoryList = findViewById(R.id.rv_history_list)
        tvDelete = findViewById(R.id.tv_delete)

        rlDeleteHistoryBar.visibility = View.GONE
        tvDelete.visibility = View.GONE
        rlTotalHistoryBar.visibility = View.VISIBLE

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        tvDelete.setOnClickListener {
            confirmAndDeleteSelected()
        }

        rvHistoryList.layoutManager = LinearLayoutManager(this)

        getMessagesNum()
        setupHistoryList()
    }

    private fun getMessagesNum() {
        lifecycleScope.launch {
            db.AiConversationDao().getConversationCount()
                .collect { count -> // 持续监听数量变化
                    withContext(Dispatchers.Main) {
                        tvTotalHistories.text = count.toString()
                    }
                }
        }
    }

    private fun setupHistoryList() {
        lifecycleScope.launch(Dispatchers.IO) {
            val list = db.AiConversationDao().getAll()
            withContext(Dispatchers.Main) {
                if (!::adapter.isInitialized) {
                    adapter = AiHistoryAdapter(
                        list,
                        onItemClick = { conv ->
                            val data = Intent().apply {
                                putExtra("conversationId", conv.conversationId)
                                putExtra("fromHistory", true)
                            }
                            setResult(RESULT_OK, data)
                            finish()
                            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                        },
                        onSelectionCountChanged = { selCount ->
                            rlDeleteHistoryBar.visibility = if (selCount > 0) View.VISIBLE else View.GONE
                            rlTotalHistoryBar.visibility = if (selCount > 0) View.GONE else View.VISIBLE
                            tvDelete.visibility = if (selCount > 0) View.VISIBLE else View.GONE
                            tvDeleteHistories.text = selCount.toString()
                        },
                        onDeleteClick = { conv ->
                            AlertDialog.Builder(this@AiHistoryActivity)
                                .setTitle("确认删除")
                                .setMessage("是否删除此条记录？")
                                .setPositiveButton("删除") { _, _ -> deleteConversations(listOf(conv)) }
                                .setNegativeButton("取消", null)
                                .show()
                        })
                    rvHistoryList.adapter = adapter
                } else {
                    adapter.submitList(list)
                }
            }
        }
    }

    private fun confirmAndDeleteSelected() {
        val toDelete = adapter.getSelectedConversations()
        if (toDelete.isEmpty()) return

        AlertDialog.Builder(this)
            .setTitle("确认删除")
            .setMessage("是否删除选中的 ${toDelete.size} 条记录？")
            .setPositiveButton("删除") { _, _ -> deleteConversations(toDelete) }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun deleteConversations(list: List<AiConversationEntity>) {
        lifecycleScope.launch(Dispatchers.IO) {
            list.forEach { conv ->
                // 删除对应消息的文件
                val msgs = db.AiMessageDao().getByConversationId(conv.conversationId)
                msgs.forEach { msg ->
                    if (msg.path.isNotBlank()) File(msg.path).takeIf { it.exists() }?.delete()
                }
                db.AiMessageDao().deleteByConversationId(conv.conversationId)
                db.AiConversationDao().deleteById(conv.conversationId)
            }
            // 重新加载列表、退出选择模式
            withContext(Dispatchers.Main) {
                setupHistoryList()
            }
        }
    }
}