package com.example.jvsglass.network

import com.example.jvsglass.utils.LogUtils
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
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

    interface ModelCallback<T> {
        fun onSuccess(result: T)
        fun onFailure(error: Throwable)
    }

    // 语音识别请求
    fun transcribeAudio(
        audioFile: File,
        callback: ModelCallback<TranscribeResponse>
    ) {
        require(setOf("pcm", "mp3", "wav", "ogg", "3gp").contains(audioFile.extension.lowercase())) {
            "Unsupported audio format"
        }

        if (!audioFile.exists() || !audioFile.canRead()) {
            callback.onFailure(IllegalArgumentException("音频文件不可用: ${audioFile.absolutePath}"))
            return
        }

        val requestFile = audioFile.asRequestBody(detectMimeType(audioFile).toMediaTypeOrNull())
        val filePart = MultipartBody.Part.createFormData("file", audioFile.name, requestFile)

        // 创建参数（修复后的timestamp参数）
        val model = "bigmodel".toRequestBody("text/plain".toMediaTypeOrNull())
        val responseFormat = "verbose_json".toRequestBody("text/plain".toMediaTypeOrNull())
        val timestampGranularities = listOf(
            MultipartBody.Part.createFormData(
                "timestamp_granularities[]",
                null,
                "word".toRequestBody("text/plain".toMediaTypeOrNull())
            )
        )

        val disposable = apiService.transcribeAudio(
            file = filePart,
            model = model,
            responseFormat = responseFormat,
            timestampGranularities = timestampGranularities
        )
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    if (response.isSuccessful) {
                        val transcribeResponse = response.body()
                        LogUtils.info("响应头: ${response.headers()}")
                        if (transcribeResponse != null) {
                            LogUtils.info("解析后的对象: $transcribeResponse")
                            callback.onSuccess(transcribeResponse)
                        } else {
                            callback.onFailure(Exception("响应体为空，HTTP 状态码: ${response.code()}"))
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "无错误体"
                        callback.onFailure(Exception("HTTP $errorCode: $errorBody"))
                    }
                },
                { callback.onFailure(it) }
            )

        compositeDisposable.add(disposable)
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
                .also { compositeDisposable.add(it) }
        }

        return MultipartBody.Part.createFormData("file", file.name, requestBody)
    }

    // 识别MIME类型
    private fun detectMimeType(file: File): String {
        return when (file.extension.lowercase()) {
            "pcm" -> "audio/pcm"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "mp4" -> "video/mp4"
            "mp3" -> "audio/mpeg"
            "pdf" -> "application/pdf"
            "3gp" -> "audio/3gpp"
            "wav" -> "audio/wav"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }

    // 清理资源
    fun dispose() {
        compositeDisposable.clear()
    }
}