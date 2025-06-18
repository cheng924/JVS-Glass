package com.example.jvsglass.network

import com.google.gson.annotations.SerializedName

data class WeatherResponse(
    @SerializedName("weather") val weather: List<WeatherItem> = emptyList(),
    @SerializedName("main") val main: Main = Main(),
    @SerializedName("dt") val timestamp: Long? = null,
    @SerializedName("name") val cityName: String? = null
)

data class WeatherItem(
    @SerializedName("id") val id: Int? = null,
    @SerializedName("main") val main: String? = null,
    @SerializedName("description") val description: String? = null,
    @SerializedName("icon") val icon: String? = null
)

data class Main(
    @SerializedName("temp") val temp: Float? = null,
    @SerializedName("feels_like") val feelsLike: Float? = null,
    @SerializedName("pressure") val pressure: Int? = null,
    @SerializedName("humidity") val humidity: Int? = null
)
