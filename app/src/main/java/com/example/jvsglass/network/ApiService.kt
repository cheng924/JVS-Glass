package com.example.jvsglass.network

import com.example.jvsglass.BuildConfig
import io.reactivex.Observable
import io.reactivex.Single
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.Headers
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    @Multipart
    @POST("v1/audio/transcriptions")
    fun transcribeAudio(
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}",
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part timestampGranularities: List<MultipartBody.Part>
    ): Observable<Response<TranscribeResponse>>

    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun chatTextCompletion(
        @Body request: ChatRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.KOUZI_AI_API_KEY}"
    ): Observable<Response<ChatResponse>>

    @POST("v1/chat/completions")
    @Headers("Content-Type: application/json")
    fun uploadImageCompletion(
        @Body request: ImageRequest,
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}"
//        @Header("Authorization") auth: String = "Bearer ${BuildConfig.KOUZI_AI_API_KEY}"
    ): Single<Response<ChatResponse>>
}