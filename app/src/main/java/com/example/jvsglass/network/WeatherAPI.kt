package com.example.jvsglass.network

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherAPI {
    @GET("data/2.5/weather")
    fun getCurrent(
        @Query("q") city: String,
        @Query("appid") key: String,
        @Query("units") units: String,
        @Query("lang") lang: String
    ): Call<WeatherResponse>
}