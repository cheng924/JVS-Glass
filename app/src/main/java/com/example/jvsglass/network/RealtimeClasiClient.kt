package com.example.jvsglass.network

import android.os.Looper
import android.util.Base64
import com.example.jvsglass.utils.LogUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okhttp3.logging.HttpLoggingInterceptor
import okio.ByteString
import java.util.concurrent.TimeUnit

class RealtimeClasiClient(
    private val apiKey: String,
    private var sourceLang: String,
    private var targetLang: String,
    private val callback: ClasiCallback
) : WebSocketListener() {
    private val tag = "RealtimeClasiClient"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private val lock = Any()
    private var isConnected = false
    private val handler = android.os.Handler(Looper.getMainLooper())
    private var reconnectionScheduled = false
    private var shouldReconnect = true
    private val audioQueue = mutableListOf<ByteArray>()
    private var isSending = false
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var isConfigSent = false

    interface ClasiCallback {
        fun onTranscriptUpdate(text: String)    // 原文更新
        fun onTranslationUpdate(text: String)   // 译文更新
        fun onFinalResult(transcript: String, translation: String)
        fun onError(error: String)
        fun onConnectionChanged(connected: Boolean)
        fun onSessionReady()
    }

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .pingInterval(10, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    fun isConnected() = isConnected && isConfigSent

    fun connect() {
        synchronized(lock) {
            if (webSocket != null) return

            val request = Request.Builder()
                .url("wss://ai-gateway.vei.volces.com/v1/realtime?model=doubao-clasi-s2t")
                .header("Authorization", "Bearer $apiKey")
                .build()

            webSocket = client.newWebSocket(request, this)
        }
    }

    fun setShouldReconnect(shouldReconnect: Boolean) {
        this.shouldReconnect = shouldReconnect
    }

    fun updateLanguages(source: String, target: String) {
        synchronized(lock) {
            webSocket?.close(1000, "Language update") // 1000 = normal closure
            webSocket = null
            isConnected = false
        }

        sourceLang = source
        targetLang = target

        synchronized(audioQueue) {
            audioQueue.clear()
        }

        shouldReconnect = true
        connect()
    }

    fun sendAudioChunk(chunk: ByteArray) {
        if (!isConnected()) {
            callback.onError("连接未就绪，请先建立连接")
            return
        }

        synchronized(audioQueue) {
            audioQueue.add(chunk)
            if (!isSending) {
                isSending = true
                sendNextChunk()
            }
        }
    }

    private fun sendNextChunk() {
        coroutineScope.launch {
            synchronized(audioQueue) {
                if (audioQueue.isNotEmpty()) {
                    val chunk = audioQueue.removeAt(0)
                    val event = JsonObject().apply {
                        addProperty("type", "input_audio_buffer.append")
                        addProperty("audio", Base64.encodeToString(chunk, Base64.NO_WRAP))
                    }
                    webSocket?.send(gson.toJson(event)) // 发送文本消息（JSON）
                    sendNextChunk()
                } else {
                    isSending = false
                }
            }
        }
    }

    fun commitAudio() {
        val doneMessage = JsonObject().apply {
            addProperty("type", "input_audio.done")
        }
        webSocket?.send(gson.toJson(doneMessage))
    }

    fun disconnect() {
        synchronized(lock) {
            coroutineScope.coroutineContext.cancel()
            handler.removeCallbacksAndMessages(null)
            webSocket?.close(1000, "Normal closure")
            webSocket = null
            isConnected = false
            callback.onConnectionChanged(false)
            shouldReconnect = false
            reconnectionScheduled = false
        }
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        isConnected = true
        isConfigSent = false
        sendSessionConfig(sourceLang, targetLang)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        val hexString = bytes.toByteArray().joinToString(" ") { String.format("%02x", it) }
        LogUtils.info("$tag Received binary message (hex): $hexString")
        val text = bytes.utf8()
        LogUtils.info("$tag Converted text: $text")
        onMessage(webSocket, text)
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        LogUtils.info("$tag onMessage (text) called with: $text")
        if (text.trim().isEmpty()) {
            return
        }
        try {
            val json = gson.fromJson(text, JsonObject::class.java)
            when (json["type"].asString) {
                "session.updated" -> {
                    isConfigSent  = true
                    callback.onSessionReady()
                }
                "response.audio_transcript.delta" -> {
                    val delta = json["delta"].asString
                    callback.onTranscriptUpdate(delta)
                }
                "response.audio_translation.delta" -> {
                    val delta = json["delta"].asString
                    callback.onTranslationUpdate(delta)
                }
                "response.done" -> {
                    val transcript = json.getAsJsonObject("response")
                        ?.get("transcript")?.asString ?: ""
                    val translation = json.getAsJsonObject("response")
                        ?.get("translation")?.asString ?: ""
                    callback.onFinalResult(transcript, translation)
                }
                "error" -> {
                    val errorMsg = json.getAsJsonObject("error")?.get("message")?.asString
                        ?: "Unknown error"
                    callback.onError(errorMsg)
                }
            }
        } catch (e: Exception) {
            callback.onError("Message parse error: ${e.message}")
        }
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        handleDisconnect(reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        callback.onError("Connection failed: ${t.message}")
        handleDisconnect(t.message ?: "Unknown error")
    }

    private fun sendSessionConfig(source: String, target: String) {
        val config = JsonObject().apply {
            addProperty("type", "session.update")
            add("session", JsonObject().apply {
                addProperty("input_audio_format", "pcm16")
                add("input_audio_translation", JsonObject().apply {
                    addProperty("source_language", source)
                    addProperty("target_language", target)
                })
            })
        }
        webSocket?.send(gson.toJson(config))
    }

    private fun handleDisconnect(reason: String) {
        isConnected = false
        callback.onConnectionChanged(false)
        if (shouldReconnect && !reconnectionScheduled) {
            reconnectionScheduled = true
            handler.postDelayed({
                if (shouldReconnect) connect()
                reconnectionScheduled = false
            }, 2000)
        }
    }
}