package com.example.jvsglass.dialog

import android.app.Dialog
import android.content.Context
import android.widget.ProgressBar
import android.widget.TextView
import android.view.Window
import android.graphics.Color
import androidx.core.graphics.drawable.toDrawable
import com.example.jvsglass.R

class WaitingDialog (context: Context) : Dialog(context) {
    private lateinit var tvMessage: TextView
    private lateinit var progressBar: ProgressBar

    init {
        initDialog()
    }

    private fun initDialog() {
        // 设置无标题栏
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        // 设置自定义布局
        setContentView(R.layout.dialog_loading)

        // 设置透明背景
        window?.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())

        // 初始化视图
        tvMessage = findViewById(R.id.tv_message)
        progressBar = findViewById(R.id.progress_bar)

        // 对话框不可取消
        setCancelable(false)
        setCanceledOnTouchOutside(false)
    }

    // 设置提示文本
    fun setMessage(message: String) {
        tvMessage.text = message
    }

    companion object {
        fun show(context: Context, message: String = "提示信息"): WaitingDialog {
            return WaitingDialog(context).apply {
                setMessage(message)
                show()
            }
        }
    }

    override fun dismiss() {
        // 安全释放资源
        super.dismiss()
    }
}