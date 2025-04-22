package com.example.jvsglass.network

import com.google.gson.annotations.SerializedName

class StreamResponse(
    @SerializedName("id") val id: String,
    @SerializedName("object") val objectType: String,
    @SerializedName("created") val created: Long,
    @SerializedName("model") val model: String,
    @SerializedName("choices") val choices: List<Choice>? = null,
    @SerializedName("usage") val usage: Usage? = null,
    @SerializedName("system_fingerprint") val systemFingerprint: String? = null
) {
    data class Choice(
        @SerializedName("index") val index: Int,
        @SerializedName("delta") val delta: Delta,
        @SerializedName("finish_reason") val finishReason: String? = null
    ) {
        data class Delta(
            @SerializedName("content") val content: String? = null
        )
    }

    data class Usage(
        @SerializedName("prompt_tokens") val promptTokens: Int,
        @SerializedName("completion_tokens") val completionTokens: Int,
        @SerializedName("total_tokens") val totalTokens: Int
    )
}