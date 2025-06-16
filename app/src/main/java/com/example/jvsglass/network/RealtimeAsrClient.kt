package com.example.jvsglass.network

import android.os.Looper
import android.util.Base64
import com.example.jvsglass.utils.LogUtils
import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

class RealtimeAsrClient(
    private val apiKey: String,
    private val callback: RealtimeAsrCallback
) : WebSocketListener() {
    private val tag = "[RealtimeAsrClient]"
    private val gson = Gson()
    private var webSocket: WebSocket? = null
    private var isSessionConfigured = false

    private var isConnected = false
    private val handler = android.os.Handler(Looper.getMainLooper())
    private var keepAlive = false
    private var disconnectRunnable: Runnable? = null
    var shouldReconnect = true
    private var reconnectionScheduled = false
    private val frameSize = 1280
    private val buffer = ByteArrayOutputStream()

    private val client = OkHttpClient.Builder()
        .pingInterval(10, TimeUnit.SECONDS)  // 保持长连接
        .retryOnConnectionFailure(true)
        .build()

    interface RealtimeAsrCallback {
        fun onPartialResult(text: String)
        fun onFinalResult(text: String)
        fun onError(error: String)
        fun onConnectionChanged(connected: Boolean)
        fun onSessionReady()
    }

    fun isConnected(): Boolean = isConnected

    fun keepConnectionOpen() {
        keepAlive = true
        // 取消任何待处理的自动断开任务
        disconnectRunnable?.let { handler.removeCallbacks(it) }
    }

    fun connect() {
        val request = Request.Builder()
            .url("wss://ai-gateway.vei.volces.com/v1/realtime?model=bigmodel")
            .header("Authorization", "Bearer $apiKey")
            .build()

        webSocket = client.newWebSocket(request, this)
    }

    fun resetSession() {
        // 清除所有待执行的任务，防止后续重连任务影响新会话
        handler.removeCallbacksAndMessages(null)
        // 主动关闭当前连接（若存在）
        webSocket?.close(1000, "Session reset")
        webSocket = null
        isConnected = false
        keepAlive = false
        reconnectionScheduled = false
        // 确保允许自动重连，重新建立会话
        shouldReconnect = true
        // 重新建立连接
        connect()
    }

    fun sendAudioChunk(chunk: ByteArray) {
        if (!isSessionConfigured) {
            LogUtils.warn("$tag 会话未配置完成")
            return
        }

        buffer.write(chunk)
        while (buffer.size() >= frameSize) {
            val data = buffer.toByteArray().copyOfRange(0, frameSize)
            sendAudio(data)
            // 剩余数据复位
            buffer.reset()
            buffer.write(buffer.toByteArray().copyOfRange(frameSize, buffer.size()))
        }
    }

    fun sendAudio(chunk: ByteArray) {
        LogUtils.debug("发送音频块: ${chunk.size}字节")

        val message = JsonObject().apply {
            addProperty("type", "input_audio_buffer.append")
            addProperty("audio", Base64.encodeToString(chunk, Base64.NO_WRAP))
        }
        webSocket?.send(gson.toJson(message)) ?: LogUtils.error("$tag WebSocket未连接")
    }

    fun commitAudio() {
        val message = JsonObject().apply {
            addProperty("type", "input_audio_buffer.commit")
        }
        webSocket?.send(gson.toJson(message))
    }

    fun disconnect() {
        handler.removeCallbacksAndMessages(null)
        webSocket?.close(1000, "Normal closure")
        webSocket = null
        isConnected = false
        keepAlive = false
        callback.onConnectionChanged(false)
        shouldReconnect = false
        reconnectionScheduled = false
    }

    override fun onOpen(webSocket: WebSocket, response: Response) {
        LogUtils.debug("WebSocket握手成功，协议版本：${response.protocol}")
        isConnected = true
        reconnectionScheduled = false
        callback.onConnectionChanged(true)
        sendSessionConfig()
    }

    override fun onMessage(webSocket: WebSocket, text: String) {
        handleServerMessage(text)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        LogUtils.info("$tag 连接关闭: $reason")
        isConnected = false
        callback.onConnectionChanged(false)
        if (!keepAlive && shouldReconnect && !reconnectionScheduled) {
            reconnectionScheduled = true
            handler.postDelayed({
                if (shouldReconnect) {
                    connect()
                }
                reconnectionScheduled = false
            }, 2000)
        }
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        LogUtils.error("$tag 连接失败: ${t.message}")
        isConnected = false
        callback.onError("连接异常: ${t.message?.substringBefore("\n")}")
        callback.onConnectionChanged(false)
        if (shouldReconnect && !reconnectionScheduled) {
            reconnectionScheduled = true
            handler.postDelayed({
                if (shouldReconnect) {
                    connect()
                }
                reconnectionScheduled = false
            }, 5000)
        }
    }

    private fun sendSessionConfig() {
        val config = JsonObject().apply {
            addProperty("type", "transcription_session.update")
            add("session", JsonObject().apply {
                addProperty("input_audio_format", "pcm")
                addProperty("input_audio_codec", "raw")
                addProperty("input_audio_sample_rate", 16000)
                addProperty("input_audio_channel", 1)
                addProperty("input_audio_bits", 16)
                add("input_audio_transcription", JsonObject().apply {
                    addProperty("model", "bigmodel")
                })
            })
        }
        webSocket?.send(gson.toJson(config))
        callback.onSessionReady()
    }

    private fun handleServerMessage(message: String) {
        LogUtils.debug("收到服务器消息: $message")
        try {
            val json = gson.fromJson(message, JsonObject::class.java)
            when (json["type"].asString) {
                "transcription_session.updated" -> {
                    LogUtils.info("$tag 会话配置已更新")
                    isSessionConfigured = true
                    callback.onSessionReady()
                }
                "conversation.item.input_audio_transcription.result" -> {
                    val transcript = json["transcript"].asString
                    LogUtils.debug("收到部分识别结果: $transcript")
                    callback.onPartialResult(transcript)
                }
                "conversation.item.input_audio_transcription.completed" -> {
                    val transcript = json["transcript"].asString
                    LogUtils.info("收到最终识别结果: $transcript")
                    callback.onFinalResult(transcript)
                }
                "error" -> {
                    val errorMsg = json.get("message")?.asString ?: "未知错误"
                    LogUtils.error("服务器返回错误: $errorMsg")
                    callback.onError("服务器错误: $errorMsg")
                }
                else -> LogUtils.warn("未知消息类型: ${json["type"].asString}")
            }
        } catch (e: Exception) {
            LogUtils.error("$tag 消息处理错误: ${e.stackTraceToString()}")
            callback.onError("Message parse error")
        }
    }
}