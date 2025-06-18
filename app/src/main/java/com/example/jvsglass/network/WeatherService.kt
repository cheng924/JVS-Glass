package com.example.jvsglass.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Call
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object WeatherService {
    private val weatherAPI: WeatherAPI

    init {
        val retrofit = Retrofit.Builder()
            .baseUrl("https://api.openweathermap.org/")
            .addConverterFactory(GsonConverterFactory.create())
            .client(createOkHttpClient())
            .build()
        weatherAPI = retrofit.create(WeatherAPI::class.java)
    }

    private fun createOkHttpClient(): OkHttpClient {
        val log = HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }
        return OkHttpClient.Builder()
            .addInterceptor(log)
            .readTimeout(15, TimeUnit.SECONDS)
            .build()
    }

    fun getCurrentWeather(city: String, apiKey: String, units: String, lang: String): Call<WeatherResponse> {
        return weatherAPI.getCurrent(city, apiKey, units, lang)
    }
}