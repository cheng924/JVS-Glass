package com.example.jvsglass.utils

import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.io.OutputStreamWriter

class FileHandler(private val activity: AppCompatActivity) {
    private var pendingContent: String = ""

    interface FileReadResultCallback {
        fun onReadSuccess(name: String, content: String)
        fun onReadFailure(errorMessage: String)
    }

    interface FileWriteResultCallback {
        fun onWriteSuccess(savedUri: Uri)
        fun onWriteFailure(errorMessage: String)
    }

    private var readCallback: FileReadResultCallback? = null
    private var writeCallback: FileWriteResultCallback? = null

    private val filePickerLauncher: ActivityResultLauncher<Array<String>> =
        activity.registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            uri?.let { handleSelectedFile(it) } ?: run {
                readCallback?.onReadFailure("文件选择已取消")
            }
        }

    private val fileSaverLauncher: ActivityResultLauncher<String> =
        activity.registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
            uri?.let { handleCreatedFile(it) } ?: run {
                writeCallback?.onWriteFailure("文件保存已取消")
            }
        }

    fun openFilePicker(callback: FileReadResultCallback) {
        this.readCallback = callback
        filePickerLauncher.launch(arrayOf("text/plain"))
    }

    fun saveFile(defaultName: String, content: String, callback: FileWriteResultCallback) {
        this.writeCallback = callback
        fileSaverLauncher.launch(defaultName)
        this.pendingContent = content
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
                            readCallback?.onReadFailure("无法获取文件名")
                            return@use null
                        }
                    }
                } else {
                    readCallback?.onReadFailure("文件元数据为空")
                    return@use null
                }
            } ?: run {
                readCallback?.onReadFailure("文件信息不可用")
                return
            }

            // 读取文件内容
            val content = activity.contentResolver.openInputStream(uri)?.use { inputStream ->
                BufferedReader(InputStreamReader(inputStream)).use { reader ->
                    reader.readText()
                }
            } ?: throw IOException("无法打开文件输入流")

            readCallback?.onReadSuccess(name, content)

        } catch (e: IOException) {
            LogUtils.error("读取文件错误: ${e.message}")
            readCallback?.onReadFailure("读取文件失败: ${e.localizedMessage}")
        } catch (e: SecurityException) {
            LogUtils.error("权限错误: ${e.message}")
            readCallback?.onReadFailure("无法获取持久化权限")
        } catch (e: Exception) {
            LogUtils.error("未知错误: ${e.message}")
            readCallback?.onReadFailure("发生未知错误: ${e.localizedMessage}")
        }
    }

    private fun handleCreatedFile(uri: Uri) {
        try {
            // 获取持久化写入权限
            activity.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

            // 写入文件内容
            activity.contentResolver.openOutputStream(uri)?.use { outputStream ->
                OutputStreamWriter(outputStream).use { writer ->
                    writer.write(pendingContent)
                }
            } ?: throw IOException("无法创建文件输出流")

            writeCallback?.onWriteSuccess(uri)
            pendingContent = ""

        } catch (e: IOException) {
            LogUtils.error("文件保存失败: ${e.message}")
        } catch (e: SecurityException) {
            LogUtils.error("缺少文件写入权限: ${e.message}")
        } catch (e: Exception) {
            LogUtils.error("保存文件时发生错误: ${e.message}")
        }
    }
}