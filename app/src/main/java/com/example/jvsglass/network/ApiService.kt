package com.example.jvsglass.network

import com.example.jvsglass.BuildConfig
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Streaming

interface ApiService {
    // 豆包预置 语音识别
    @Multipart
    @POST("v1/audio/transcriptions")
    fun transcribeAudio(
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}",
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part timestampGranularities: List<MultipartBody.Part>
    ): Observable<Response<TranscribeResponse>>

    // Coze工作流 doubao-1.5-pro-32k
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun chatTextCompletion(
        @Body request: ChatRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.KOUZI_AI_API_KEY}"
    ): Observable<Response<ChatResponse>>

    // Coze工作流 doubao-1.5-pro-32k（流式响应）
    @Streaming
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun chatTextCompletionStream(
        @Body request: ChatRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.KOUZI_AI_API_KEY}"
    ): Observable<ResponseBody>

    // 豆包预置 doubao-1.5-vision-pro-32k
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun uploadImageCompletion(
        @Body request: ImageRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}"
    ): Single<Response<ChatResponse>>

    // Coze工作流 doubao-1.5-vision-pro-32k
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun uploadImageCozeCompletion(
        @Body request: ImageCozeRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.KOUZI_AI_API_KEY}"
    ): Single<Response<ChatResponse>>

    // 豆包预置 doubao-1.5-pro-256k
    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun uploadFileTextCompletion(
        @Body request: ChatRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}"
    ): Single<Response<ChatResponse>>
}