package com.example.jvsglass.network

import io.reactivex.Observable
import okhttp3.MultipartBody
import okhttp3.RequestBody
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

    // 多文件批量上传
    @Multipart
    @POST("post")
    fun uploadMultipleFiles(
        @Part parts: List<MultipartBody.Part>,
        @Part("description") description: RequestBody
    ): Observable<UploadResult>
}