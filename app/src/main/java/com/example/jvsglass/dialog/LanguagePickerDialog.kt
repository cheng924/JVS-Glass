package com.example.jvsglass.dialog

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import com.example.jvsglass.R

class LanguagePickerDialog(
    context: Context,
    private val currentLanguage: String,
    private val onConfirm: (String) -> Unit
) : Dialog(context, R.style.button_sheet_dialog) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_language_picker)

        setCancelable(false)
        setCanceledOnTouchOutside(false)

        window?.apply {
            // 再保证一下：宽度包裹内容，高度随内容
            setLayout(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setGravity(Gravity.CENTER)
            // 半透明背景遮罩
            setDimAmount(0.5f)
        }

        val languagePickView = findViewById<LanguagePickView>(R.id.languagePickView)
        val btnConfirm = findViewById<Button>(R.id.btn_confirm)

        languagePickView.setCurrentLanguage(currentLanguage)
        btnConfirm.setOnClickListener {
            // 获取选中的语言
            onConfirm.invoke(languagePickView.getCurrentLanguage())
            dismiss()
        }
    }
}