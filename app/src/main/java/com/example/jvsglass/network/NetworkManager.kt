package com.example.jvsglass.network

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File

class NetworkManager private constructor() {

    // 单例模式
    companion object {
        @Volatile private var instance: NetworkManager? = null

        fun getInstance(): NetworkManager {
            return instance ?: synchronized(this) {
                instance ?: NetworkManager().also { instance = it }
            }
        }
    }

    // Retrofit服务接口
    private val apiService: ApiService by lazy {
        RetrofitClient.instance.create(ApiService::class.java)
    }

    // 统一管理Disposable
    private val compositeDisposable = CompositeDisposable()

    // 回调接口
    interface UploadCallback {
        fun onSuccess(result: UploadResult)
        fun onFailure(error: Throwable)
        fun onProgress(percent: Float) // 进度范围: 0.0f ~ 1.0f
    }

    // 单文件上传
    fun uploadSingleFile(
        file: File,
        description: String,
        callback: UploadCallback
    ) {
        val filePart = createFilePart(file, callback)
        val descBody = createTextBody(description)

        val disposable = apiService.uploadSingleFile(filePart, descBody)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { callback.onSuccess(it) },
                { callback.onFailure(it) }
            )

        compositeDisposable.add(disposable)
    }

    // 多文件批量上传
    fun uploadMultipleFiles(
        files: List<File>,
        description: String,
        callback: UploadCallback
    ) {
        val parts = files.map { createFilePart(it, callback) }
        val descBody = createTextBody(description)

        val disposable = apiService.uploadMultipleFiles(parts, descBody)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { callback.onSuccess(it) },
                { callback.onFailure(it) }
            )

        compositeDisposable.add(disposable)
    }

    // 创建文本RequestBody
    private fun createTextBody(text: String): RequestBody {
        return text.toRequestBody("text/plain".toMediaType())
    }

    // 创建文件Part（含进度监听）
    private fun createFilePart(file: File, callback: UploadCallback): MultipartBody.Part {
        val mimeType = detectMimeType(file)
        val requestBody = ProgressRequestBody(file, mimeType) { percent ->
            Observable.just(percent)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe { callback.onProgress(it) }
                .also { compositeDisposable.add(it) } // 修复CheckResult警告
        }

        return MultipartBody.Part.createFormData("file", file.name, requestBody)
    }

    // 识别MIME类型
    private fun detectMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            else -> "application/octet-stream"
        }
    }

    // 清理资源
    fun dispose() {
        compositeDisposable.clear()
    }
}