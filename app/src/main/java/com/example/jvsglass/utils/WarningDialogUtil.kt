package com.example.jvsglass.utils

import android.app.AlertDialog
import android.content.Context
import com.example.jvsglass.R

class WarningDialogUtil {

    interface DialogButtonClickListener {
        fun onPositiveButtonClick()
        fun onNegativeButtonClick()
    }

    companion object {
        fun showDialog(
            context: Context,
            title: String? = null,
            message: String? = null,
            positiveButtonText: String? = null,
            negativeButtonText: String? = null,
            listener: DialogButtonClickListener?
        ) {
            val builder = AlertDialog.Builder(context).apply {
                setTitle(title)
                setMessage(message)
                setCancelable(false)

                setPositiveButton(positiveButtonText) { dialog, _ ->
                    listener?.onPositiveButtonClick()
                    dialog.dismiss()
                }

                setNegativeButton(negativeButtonText) { dialog, _ ->
                    listener?.onNegativeButtonClick()
                    dialog.dismiss()
                }
            }

            val dialog = builder.create()
            dialog.show()

            dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(context.getColor(R.color.black))
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(context.getColor(R.color.black))
        }
    }
}