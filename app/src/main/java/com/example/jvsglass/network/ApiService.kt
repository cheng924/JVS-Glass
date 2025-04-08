package com.example.jvsglass.network

import com.example.jvsglass.BuildConfig
import io.reactivex.Observable
import okhttp3.MultipartBody
import okhttp3.RequestBody
import retrofit2.Response
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part

interface ApiService {
    // 单文件上传
    @Multipart
    @POST("post")
    fun uploadSingleFile(
        @Part file: MultipartBody.Part,
        @Part("description") description: RequestBody
    ): Observable<UploadResult>

    @Multipart
    @POST("v1/audio/transcriptions")
    fun transcribeAudio(
        @Header("Authorization") auth: String = "Bearer ${BuildConfig.DOUBAO_AI_API_KEY}",
        @Part file: MultipartBody.Part,
        @Part("model") model: RequestBody,
        @Part("response_format") responseFormat: RequestBody,
        @Part timestampGranularities: List<MultipartBody.Part>
    ): Observable<Response<TranscribeResponse>>
}