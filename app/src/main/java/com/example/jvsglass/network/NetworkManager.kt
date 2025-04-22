package com.example.jvsglass.network

import android.util.Base64
import com.example.jvsglass.BuildConfig
import com.example.jvsglass.utils.LogUtils
import com.google.gson.Gson
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.disposables.Disposable
import io.reactivex.schedulers.Schedulers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.Response
import java.io.File
import java.net.URLConnection

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

    interface ModelCallback<T> {
        fun onSuccess(result: T)
        fun onFailure(error: Throwable)
    }

    interface StreamCallback {
        fun onNewMessage(text: String)  // 收到新的增量文本片段
        fun onCompleted()   // 流结束
        fun onError(error: Throwable)   // 出错
    }

    // 语音识别
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

        // 创建参数
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

    // DouBao文字聊天
    fun chatTextCompletion(
        messages: List<ChatRequest.Message>,
        temperature: Double = 0.7,
        callback: ModelCallback<ChatResponse>
    ) {
        // 参数校验
        require(messages.any { it.role == "user" }) { "至少需要一条用户消息" }
        require(temperature in 0.0..2.0) { "temperature值需在0.0到2.0之间" }

        val request = ChatRequest(
            model = "7491227972067377162",
            messages = messages,
            temperature = temperature
        )

        val disposable = apiService.chatTextCompletion(request = request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    if (response.isSuccessful) {
                        val chatResponse = response.body()
                        if (chatResponse != null) {
                            LogUtils.info("大模型响应: $chatResponse")
                            callback.onSuccess(chatResponse)
                        } else {
                            callback.onFailure(Exception("空响应体，状态码: ${response.code()}"))
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "无错误详情"
                        callback.onFailure(Exception("HTTP $errorCode: $errorBody"))
                    }
                },
                { error ->
                    LogUtils.error("请求失败", error)
                    callback.onFailure(error)
                }
            )

        compositeDisposable.add(disposable)
    }

    // DouBao文字聊天（流式响应）
    fun chatTextCompletionStream(
        messages: List<ChatRequest.Message>,
        temperature: Double = 0.7,
        callback: StreamCallback
    ): Disposable {
        require(messages.any { it.role == "user" }) { "至少需要一条用户消息" }
        val request = ChatRequest(
            model = "7491227972067377162",
            messages = messages,
            temperature = temperature,
            stream = true
        )

        return apiService.chatTextCompletionStream(request)
            .subscribeOn(Schedulers.io())
            .flatMap { body ->
                Observable.create<String> { emitter ->
                    val source = body.source()
                    try {
                        while (!source.exhausted() && !emitter.isDisposed) {
                            val line = source.readUtf8Line() ?: break
                            if (!line.startsWith("data:")) continue

                            val payload = line.removePrefix("data:").trim()
                            if (payload == "[DONE]") {
                                emitter.onComplete()
                                break
                            }

                            val chunk = Gson().fromJson(payload, StreamResponse::class.java)
                            val content = chunk.choices?.firstOrNull()?.delta?.content
                            if (!content.isNullOrEmpty()) {
                                emitter.onNext(content)
                            }
                        }
                    } catch (e: Exception) {
                        if (!emitter.isDisposed) emitter.onError(e)
                    } finally {
                        body.close()
                    }
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { text -> callback.onNewMessage(text) },
                { err -> callback.onError(err) },
                { callback.onCompleted() }
            )
    }

    // DouBao图片识别
    fun uploadImageCompletion(
        images: List<File>,
        question: String?,
        callback: ModelCallback<ChatResponse>
    ) {
        require(images.size in 1..9) { "图片数量需在1到9张之间" }

        Observable.fromCallable {
            val contentItems = mutableListOf<ImageRequest.ContentItem>().apply {
                question?.takeIf { it.isNotEmpty() }?.let {
                    add(ImageRequest.ContentItem.createTextContent(it))
                }

                images.forEach { file ->
                    val mimeType = when (file.extension.lowercase()) {
                        "jpg", "jpeg" -> "image/jpeg"
                        "png" -> "image/png"
                        else -> "application/octet-stream"
                    }
                    val base64 = Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)
                    add(ImageRequest.ContentItem.createImageContent(base64, mimeType))
                }
            }

            ImageRequest(
                model = "doubao-1.5-vision-pro-32k",
                messages = listOf(ImageRequest.Message(role = "user", content = contentItems)),
                maxTokens = 300,
            )
        }
            .subscribeOn(Schedulers.io())
            .flatMap { request: ImageRequest ->
                apiService.uploadImageCompletion(request)
                    .toObservable()
                    .onErrorResumeNext { throwable: Throwable ->
                        Observable.error(throwable)
                    }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response: Response<ChatResponse> ->
                    when {
                        response.isSuccessful && response.body() != null ->
                            callback.onSuccess(response.body()!!)
                        response.isSuccessful ->
                            callback.onFailure(Exception("Empty response body"))
                        else -> {
                            val errorBody = response.errorBody()?.string()?.take(200) ?: "Unknown error"
                            callback.onFailure(Exception("HTTP $response.code(): $errorBody"))
                        }
                    }
                },
                { error: Throwable ->
                    LogUtils.error("Upload failed", error)
                    callback.onFailure(error)
                }
            ).also { disposable: Disposable ->
                compositeDisposable.add(disposable)
            }
    }

    // Coze-DouBao图片识别
    fun uploadImageCozeCompletion(
        images: List<String>,
        question: String?,
        callback: ModelCallback<ChatResponse>
    ) {
        require(images.size in 1..9) { "图片数量需在1到9张之间" }

        Observable.fromCallable {
            val contentItems = mutableListOf<ImageCozeRequest.ContentItem>().apply {
                question?.takeIf { it.isNotEmpty() }?.let {
                    add(ImageCozeRequest.ContentItem.createTextContent(it))
                }

                images.forEach { url ->
                    add(ImageCozeRequest.ContentItem.createImageContent(url))
                }
            }

            ImageCozeRequest(
                model = "7491501936588439591",
                messages = listOf(ImageCozeRequest.Message(role = "user", content = contentItems)),
                maxTokens = 300,
            )
        }
        .subscribeOn(Schedulers.io())
        .flatMap { request: ImageCozeRequest ->
            apiService.uploadImageCozeCompletion(request)
                .toObservable()
                .onErrorResumeNext { throwable: Throwable ->
                    Observable.error(throwable)
                }
        }
        .observeOn(AndroidSchedulers.mainThread())
        .subscribe(
            { response: Response<ChatResponse> ->
                when {
                    response.isSuccessful && response.body() != null ->
                        callback.onSuccess(response.body()!!)
                    response.isSuccessful ->
                        callback.onFailure(Exception("Empty response body"))
                    else -> {
                        val errorBody = response.errorBody()?.string()?.take(200) ?: "Unknown error"
                        callback.onFailure(Exception("HTTP $response.code(): $errorBody"))
                    }
                }
            },
            { error: Throwable ->
                LogUtils.error("Upload failed", error)
                callback.onFailure(error)
            }
        ).also { disposable: Disposable ->
            compositeDisposable.add(disposable)
        }
    }

    fun uploadFileTextCompletion(
        messages: List<ChatRequest.Message>,
        temperature: Double = 0.7,
        callback: ModelCallback<ChatResponse>
    ) {
        val request = ChatRequest(
            model = "doubao-1.5-pro-256k",
            messages = messages,
            temperature = temperature
        )

        val disposable = apiService.uploadFileTextCompletion(request = request)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { response ->
                    if (response.isSuccessful) {
                        val chatResponse = response.body()
                        if (chatResponse != null) {
                            LogUtils.info("大模型响应: $chatResponse")
                            callback.onSuccess(chatResponse)
                        } else {
                            callback.onFailure(Exception("空响应体，状态码: ${response.code()}"))
                        }
                    } else {
                        val errorCode = response.code()
                        val errorBody = response.errorBody()?.string() ?: "无错误详情"
                        callback.onFailure(Exception("HTTP $errorCode: $errorBody"))
                    }
                },
                { error ->
                    LogUtils.error("请求失败", error)
                    callback.onFailure(error)
                }
            )

        compositeDisposable.add(disposable)
    }

    fun createRealtimeAsrClient(callback: RealtimeAsrClient.RealtimeAsrCallback): RealtimeAsrClient {
        return RealtimeAsrClient(
            apiKey = BuildConfig.DOUBAO_AI_API_KEY,
            callback = callback
        )
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
            else -> URLConnection.guessContentTypeFromName(file.name) ?: "application/octet-stream"
        }
    }

    // 清理资源
    fun dispose() {
        compositeDisposable.clear()
    }
}