package com.example.jvsglass.activities.teleprompter

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.jvsglass.R
import com.example.jvsglass.database.AppDatabase
import com.example.jvsglass.database.AppDatabaseProvider
import com.example.jvsglass.database.TeleprompterArticle
import com.example.jvsglass.database.toFileItem
import com.example.jvsglass.utils.FileHandler
import com.example.jvsglass.utils.LogUtils
import com.example.jvsglass.utils.ToastUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TeleprompterActivity : AppCompatActivity(), FileHandler.FileReadResultCallback {
    private val db: AppDatabase by lazy { AppDatabaseProvider.db }
    private lateinit var adapter: FileAdapter
    private lateinit var fileHandler: FileHandler

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_teleprompter)

        val rlTeleprompterBar: RelativeLayout = findViewById(R.id.rl_teleprompter_bar)
        val rlSelectBar: RelativeLayout = findViewById(R.id.rl_select_bar)
        val llFileBar: LinearLayout = findViewById(R.id.ll_file_bar)
        val llDeleteBar: LinearLayout = findViewById(R.id.ll_delete_bar)
        val recyclerView: RecyclerView = findViewById(R.id.rvFiles)
        val rlTotalFileBar: RelativeLayout = findViewById(R.id.rl_total_file_bar)
        val rlDeleteFileBar: RelativeLayout = findViewById(R.id.rl_delete_file_bar)

        val tvTotalFiles: TextView = findViewById(R.id.tv_total_files)
        val tvDeleteFiles: TextView = findViewById(R.id.tv_delete_files)

        adapter = FileAdapter().apply {
            onSelectionModeChangeListener = object : FileAdapter.OnSelectionModeChangeListener {
                override fun onSelectionModeChanged(isActive: Boolean) {
                    rlTeleprompterBar.visibility = if (isActive) View.GONE else View.VISIBLE
                    llFileBar.visibility = if (isActive) View.GONE else View.VISIBLE
                    rlTotalFileBar.visibility = if (isActive) View.GONE else View.VISIBLE
                    rlSelectBar.visibility = if (isActive) View.VISIBLE else View.GONE
                    llDeleteBar.visibility = if (isActive) View.VISIBLE else View.GONE
                    rlDeleteFileBar.visibility = if (isActive) View.VISIBLE else View.GONE
                }

                override fun onSelectedItemsChanged(selectedCount: Int) {
                    tvDeleteFiles.text = "$selectedCount"
                }
            }
        }

        recyclerView.layoutManager = GridLayoutManager(this, 1)
        recyclerView.adapter = adapter

        // 文本数据加载
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                db.TeleprompterArticleDao().getAll().collect { articles ->
                    val fileItems = articles.map { it.toFileItem() }
                    adapter.submitList(fileItems)
                }
            }
        }

        findViewById<ImageView>(R.id.btnBack).setOnClickListener {
            finish()
            overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, R.anim.slide_in_left, R.anim.slide_out_right)
        }

        findViewById<LinearLayout>(R.id.btnNew).setOnClickListener {
            startActivity(Intent(this, TeleprompterNewFileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.btnImport).setOnClickListener {
            fileHandler.openFilePicker(this)
        }

        findViewById<LinearLayout>(R.id.ll_delete_bar).setOnClickListener {
            val selectedFiles = adapter.selectedItem.map { position ->
                adapter.currentList[position].fileDate
            }
            deleteFiles(selectedFiles)
        }

        findViewById<TextView>(R.id.tv_select_done).setOnClickListener {
            adapter.exitSelectionMode()
        }

        findViewById<TextView>(R.id.tv_select_all).setOnClickListener {
            adapter.selectAll()
        }

        fileHandler = FileHandler(this)
        getFilesNum(tvTotalFiles)
    }

    private fun getFilesNum(tvTotalFiles: TextView) {
        lifecycleScope.launch {
            try {
                db.TeleprompterArticleDao().getArticleCount()
                    .collect { count -> // 持续监听数量变化
                        withContext(Dispatchers.Main) {
                            tvTotalFiles.text = "$count"
                        }
                    }
            } catch (e: Exception) {
                LogUtils.info("获取失败: ${e.message}")
            }
        }
    }

    private fun deleteFiles(selectedFiles: List<String>) {
        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    selectedFiles.forEach { date ->
                        db.TeleprompterArticleDao().delete(date)
                    }
                }
                withContext(Dispatchers.Main) {
                    adapter.exitSelectionMode()
                    LogUtils.info("删除成功")
                }
            } catch (e: Exception) {
                LogUtils.info("删除失败: ${e.message}")
            }
        }
    }

    override fun onReadSuccess(name: String, content: String) {
        val fileName = name.substringBeforeLast(".", missingDelimiterValue = name)
        val fileDate = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"))

        saveToDatabase(fileName, fileDate, content)

        val intent = Intent(this, TeleprompterDisplayActivity::class.java).apply {
            putExtra("fileName", fileName)
            putExtra("fileDate", fileDate)
            putExtra("fileContent", content)
        }
        startActivity(intent)
    }

    override fun onReadFailure(errorMessage: String) {
        // 处理文件读取失败
        LogUtils.error("错误: $errorMessage")
    }

    private fun saveToDatabase(fileName: String, fileDate: String, fileContent: String) {
        val article = TeleprompterArticle(
            title = fileName,
            createDate = fileDate,
            content = fileContent
        )

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                db.TeleprompterArticleDao().insert(article)
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@TeleprompterActivity, "保存成功")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    ToastUtils.show(this@TeleprompterActivity, "保存失败：${e.message}")
                }
            }
        }
    }
}