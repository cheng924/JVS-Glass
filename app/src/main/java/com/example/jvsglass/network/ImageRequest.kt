package com.example.jvsglass.network

import com.google.gson.annotations.SerializedName

data class ImageRequest(
    @SerializedName("model") val model: String,
    @SerializedName("messages") val messages: List<Message>,
    @SerializedName("max_tokens") val maxTokens: Int = 300
) {
    data class Message(
        @SerializedName("role") val role: String,
        @SerializedName("content") val content: List<ContentItem>
    )

    data class ContentItem(
        @SerializedName("type") val type: String,
        @SerializedName("text") val text: String? = null,
        @SerializedName("image_url") val imageUrl: ImageUrl? = null
    ) {
        companion object {
            private const val TYPE_TEXT = "text"
            private const val TYPE_IMAGE = "image_url"

            fun createTextContent(text: String) = ContentItem(
                type = TYPE_TEXT,
                text = text
            )

            fun createImageContent(base64Data: String, mimeType: String) = ContentItem(
                type = TYPE_IMAGE,
                imageUrl = ImageUrl(
                    url = "data:$mimeType;base64,$base64Data"
                )
            )
        }
    }

    data class ImageUrl(
        @SerializedName("url") val url: String
    )
}
