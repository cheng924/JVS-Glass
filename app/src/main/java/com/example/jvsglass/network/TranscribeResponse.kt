package com.example.jvsglass.network

import com.google.gson.annotations.SerializedName

data class TranscribeResponse(
    @SerializedName("text") val text: String,
    @SerializedName("duration") val duration: Double,
    @SerializedName("segments") val segments: List<Segment>,
    @SerializedName("words") val words: List<Word>?,
    @SerializedName("task") val task: String? = null,
    @SerializedName("language") val language: String? = null
) {
    data class Segment(
        @SerializedName("id") val id: Int,
        @SerializedName("text") val text: String,
        @SerializedName("start") val start: Double,
        @SerializedName("end") val end: Double
    )

    data class Word(
        @SerializedName("word") val text: String,
        @SerializedName("start") val start: Double,
        @SerializedName("end") val end: Double
    )
}
