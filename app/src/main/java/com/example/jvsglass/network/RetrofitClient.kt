package com.example.jvsglass.network

import com.example.jvsglass.utils.LogUtils
import com.google.gson.GsonBuilder
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val BASE_URL = "https://ai-gateway.vei.volces.com/"
    private val gson = GsonBuilder().setLenient().create()

    // Retrofit自带的日志拦截器
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY // 输出请求和响应详情
    }

    // 打印头部并禁用gzip拦截器
    private val headerInterceptor = Interceptor { chain ->
        // 拦截请求，打印并修改Accept-Encoding
        val original = chain.request()
        val req = original.newBuilder()
            .header("Accept-Encoding", "identity") // 强制不压缩
            .build()
        LogUtils.info("→ Request → ${req.method} ${req.url}")
        req.headers.toMultimap().forEach { (name, values) ->
            values.forEach { value ->
                LogUtils.debug("   • $name: $value")
            }
        }

        val resp = chain.proceed(req)

        // 打印响应头
        LogUtils.info("← Response ← ${resp.code} ${resp.message}")
        resp.headers.toMultimap().forEach { (name, values) ->
            values.forEach { value ->
                LogUtils.debug("   • $name: $value")
            }
        }

        resp
    }

    // 自定义OkHttpClient（添加日志拦截器、超时设置）
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(headerInterceptor)
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    // 构建Retrofit实例
    val instance: Retrofit by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
            .build()
    }
}