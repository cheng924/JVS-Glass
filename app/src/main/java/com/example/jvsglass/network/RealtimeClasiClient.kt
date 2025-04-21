package com.example.jvsglass.network

import android.os.Looper
import android.util.Base64
import com.example.jvsglass.utils.LogUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ObsoleteCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.ticker
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
    private val coroutineScope = CoroutineScope(Dispatchers.IO + Job())
    private var isConfigSent = false
    private var senderJob: Job? = null

    private val maxPacketDurationMs = 200
    private val bytesPerSample = 2 // PCM16
    private val sampleRate = 16000 // Hz
    private val channels = 1
    private val bytesPerMs: Int = sampleRate * bytesPerSample * channels / 1000
    private val targetPacketSizeBytes: Int = bytesPerMs * maxPacketDurationMs

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
            webSocket?.close(1000, "Language update")
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
        }
        startSenderIfNeeded()
    }

    @OptIn(ObsoleteCoroutinesApi::class)
    private fun startSenderIfNeeded() {
        if (senderJob?.isActive == true) return
        senderJob = CoroutineScope(Dispatchers.IO + Job()).launch {
            val tickerChannel = ticker(delayMillis = 100, initialDelayMillis = 0)
            try {
                for (unit in tickerChannel) {
                    // 合并多帧至一个包
                    val packetChunks = mutableListOf<ByteArray>()
                    var accumulatedSize = 0
                    synchronized(audioQueue) {
                        while (audioQueue.isNotEmpty() && accumulatedSize < targetPacketSizeBytes) {
                            val c = audioQueue.removeAt(0)
                            packetChunks.add(c)
                            accumulatedSize += c.size
                        }
                    }
                    if (packetChunks.isEmpty()) continue
                    // 合并字节数组
                    val merged = ByteArray(accumulatedSize)
                    var offset = 0
                    for (c in packetChunks) {
                        System.arraycopy(c, 0, merged, offset, c.size)
                        offset += c.size
                    }

                    val event = JsonObject().apply {
                        addProperty("type", "input_audio_buffer.append")
                        addProperty("audio", Base64.encodeToString(merged, Base64.NO_WRAP))
                    }
                    webSocket?.send(gson.toJson(event))
                }
            } finally {
                tickerChannel.cancel()
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
            senderJob?.cancel()
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