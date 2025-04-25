package com.example.jvsglass.utils

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.FragmentActivity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class SystemFileOpener(private val context: Context) {

    // 用于保存相机拍摄的照片URI
    private var cameraImageUri: Uri? = null

    // 定义回调接口
    interface FileResultCallback {
        fun onCameraPhotoCaptured(uri: Uri?)
        fun onFileSelected(path: String?)
        fun onError(errorMessage: String)
    }

    // 注册Activity结果启动器
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var pickImageLauncher: ActivityResultLauncher<String>
    private lateinit var cameraLauncher: ActivityResultLauncher<Intent>
    private lateinit var folderLauncher: ActivityResultLauncher<Intent>

    // 初始化所有启动器
    fun registerLaunchers(activity: FragmentActivity, callback: FileResultCallback) {
        pickImageLauncher = activity.registerForActivityResult(
            ActivityResultContracts.GetContent()
        ) { uri: Uri? ->
            if (uri != null) {
                callback.onFileSelected(uri.toString())
            } else {
                callback.onError("未选择图片")
            }
        }

        requestPermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            if (isGranted) {
                openCamera()
            } else {
                callback.onError("需要相机权限才能拍照")
            }
        }

        cameraLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                callback.onCameraPhotoCaptured(cameraImageUri)
            } else {
                cameraImageUri?.let { uri ->
                    context.contentResolver.delete(uri, null, null)
                }
                callback.onError("拍照取消或失败")
            }
        }

        folderLauncher = activity.registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uri = result.data?.data
                if (uri == null) {
                    callback.onError("未选择文件或图片")
                } else {
                    thread {
                        val mime = context.contentResolver.getType(uri) ?: ""
                        val newPath = if (mime.startsWith("image/")) {
                            copyGalleryImageToAppDir(uri)
                        } else {
                            copySelectedFile(uri)
                        }
                        Handler(Looper.getMainLooper()).post {
                            if (newPath != null) callback.onFileSelected(newPath)
                            else callback.onError("文件复制失败")
                        }
                    }
                }
            }
        }
    }

    // 打开相册
    fun openGallery() {
        pickImageLauncher.launch("image/*")
    }

    // 打开相机
    fun openCamera() {
        when {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED -> {
                val photoFile = createImageFile()
                photoFile?.let { file ->
                    cameraImageUri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.provider",
                        file
                    )
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                        putExtra(MediaStore.EXTRA_OUTPUT, cameraImageUri)
                        addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    }
                    safeLaunchIntent(intent, cameraLauncher, "未找到相机应用")
                } ?: run {
                    ToastUtils.show(context, "无法创建照片文件")
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    // 打开文件选择器
    fun openFilePicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
            putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(
                "application/pdf",
                "application/msword",
                "text/plain"
            ))
        }
        safeLaunchIntent(intent, folderLauncher, "无法打开文件选择器")
    }

    private fun safeLaunchIntent(
        intent: Intent,
        launcher: ActivityResultLauncher<Intent>,
        errorMessage: String
    ) {
        try {
            launcher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            LogUtils.error(errorMessage)
        } catch (e: Exception) {
            LogUtils.error("发生未知错误: ${e.message}")
        }
    }

    private fun createImageFile(): File? {
        // 存储路径：Android/data/<包名>/files/Pictures/JPEG_时间戳.jpg
        // 时间戳精确到毫秒
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmssSSS", Locale.getDefault()).format(Date())
        val fileName = "JPEG_${timeStamp}.jpg"
        return context.getExternalFilesDir(null)?.let { storageDir ->
            File(storageDir, fileName).apply {
                if (exists()) delete()
                createNewFile() // 显式创建文件
            }
        }
    }

    fun copyGalleryImageToAppDir(uri: Uri): String? {
        return try {
            val imgFile = createImageFile() ?: return null
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(imgFile).use { output ->
                    input.copyTo(output)
                }
            }
            imgFile.absolutePath
        } catch (e: Exception) {
            LogUtils.error("复制相册图片失败: ${e.message}")
            null
        }
    }

    private fun copySelectedFile(uri: Uri): String? {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val originalName = getFileName(uri) ?: "unknown_${System.currentTimeMillis()}"
        val newFileName = "FILE_${System.currentTimeMillis()}_${originalName}"

        return try {
            val outputDir = context.getExternalFilesDir(null)
            val outputFile = File(outputDir, newFileName).apply {
                if (exists()) delete()
                createNewFile()
            }

            FileOutputStream(outputFile).use { output ->
                inputStream.copyTo(output)
            }
            outputFile.absolutePath
        } catch (e: Exception) {
            LogUtils.error("文件复制失败: ${e.message}")
            null
        }
    }

    private fun getFileName(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> {
                context.contentResolver.query(uri,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }
            }
            "file" -> uri.lastPathSegment
            else -> null
        }
    }
}