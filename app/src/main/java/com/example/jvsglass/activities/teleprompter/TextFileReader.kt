package com.example.jvsglass.activities.teleprompter

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.example.jvsglass.utils.LogUtils
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader

class TextFileReader(private val activity: AppCompatActivity) {
    interface FileReadResultCallback {
        fun onSuccess(name: String, content: String)
        fun onFailure(errorMessage: String)
    }

    private var resultCallback: FileReadResultCallback? = null

    private val filePickerLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleSelectedFile(it) } ?: run {
                resultCallback?.onFailure("文件选择已取消")
            }
        }

    fun openFilePicker(callback: FileReadResultCallback) {
        this.resultCallback = callback
        filePickerLauncher.launch(arrayOf("text/plain"))
    }

    private fun handleSelectedFile(uri: Uri) {
        try {
            // 获取持久化权限
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )

            // 获取文件名
            val name = activity.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    when {
                        nameIndex >= 0 -> cursor.getString(nameIndex)
                        else -> {
                            resultCallback?.onFailure("无法获取文件名")
                            return@use null
                        }
                    }
                } else {
                    resultCallback?.onFailure("文件元数据为空")
                    return@use null
                }
            } ?: run {
                resultCallback?.onFailure("文件信息不可用")
                return
            }

            // 读取文件内容
            val content = activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw IOException("无法打开文件输入流")

            resultCallback?.onSuccess(name, content)

        } catch (e: IOException) {
            LogUtils.error("读取文件错误: ${e.message}")
            resultCallback?.onFailure("读取文件失败: ${e.localizedMessage}")
        } catch (e: SecurityException) {
            LogUtils.error("权限错误: ${e.message}")
            resultCallback?.onFailure("无法获取持久化权限")
        } catch (e: Exception) {
            LogUtils.error("未知错误: ${e.message}")
            resultCallback?.onFailure("发生未知错误: ${e.localizedMessage}")
        }
    }
}