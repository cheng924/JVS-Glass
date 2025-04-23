package com.example.jvsglass.activities.jvsai

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.ImageView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AiHistoryActivity : AppCompatActivity() {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var adapter: ConversationAdapter
    private lateinit var rvHistoryList: RecyclerView

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_history)

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
        }

        rvHistoryList = findViewById(R.id.rvHistoryList)
        rvHistoryList.layoutManager = LinearLayoutManager(this)

        lifecycleScope.launch(Dispatchers.IO) {
            val list = db.AiConversationDao().getAll()
            withContext(Dispatchers.Main) {
                adapter = ConversationAdapter(list) { conv ->
                    val intent = Intent(this@AiHistoryActivity, JVSAIActivity::class.java).apply {
                        putExtra("conversationId", conv.conversationId)
                        flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                    }
                    startActivity(intent)
                    finish()
                    overridePendingTransition(R.anim.slide_in_left, R.anim.slide_out_right)
                }
                rvHistoryList.adapter = adapter
            }
        }
    }
}