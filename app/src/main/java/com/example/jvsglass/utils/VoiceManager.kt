package com.example.jvsglass.utils

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceManager(private val context: Context) {
    private var audioRecord: AudioRecord? = null
    private var recordingThread: Thread? = null
    private var isRecording = false

    // 录音参数
    private val sampleRate = 16000      // 16kHz采样率
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO // 单声道
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT // 16bit位宽

    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioPath: String? = null
    private var currentPlayingPath: String? = null

    interface AudioRecordCallback {
        fun onAudioData(data: ByteArray) // 实时返回音频数据块
    }

    fun isRecording() = isRecording

    // 录音控制
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startRecording(callback: AudioRecordCallback): String? {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val fileName = "$timestamp.pcm"
        val outputFile = File(context.getExternalFilesDir(null), fileName) // 使用持久化存储

        // 计算缓冲区大小
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        if (bufferSize < 0) {
            LogUtils.error("[VoiceManager] 无效的缓冲区大小")
            return null
        }

        return try {
            // 初始化AudioRecord
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2 // 双倍缓冲区防止溢出
            )

            audioRecord!!.startRecording()
            isRecording = true

            // 创建录音线程
            recordingThread = Thread {
                val buffer = ByteArray(bufferSize)
                FileOutputStream(outputFile).use { fos ->
                    while (isRecording) {
                        val bytesRead = audioRecord!!.read(buffer, 0, bufferSize)
                        if (bytesRead > 0) {
                            val audioData = buffer.copyOf(bytesRead)
                            callback.onAudioData(audioData) // 实时回调音频数据
                            fos.write(audioData, 0, bytesRead) // 同时写入文件（可选）
                        }
                    }
                }
            }.apply { start() }

            currentAudioPath = outputFile.absolutePath
            LogUtils.info("[VoiceManager] 开始PCM录音: $currentAudioPath")
            currentAudioPath
        } catch (e: Exception) {
            LogUtils.error("[VoiceManager] 录音失败: ${e.stackTraceToString()}")
            null
        }
    }

    fun stopRecording() {
        isRecording = false
        audioRecord?.apply {
            stop()
            release()
        }
        audioRecord = null
        recordingThread?.join() // 等待线程结束
        recordingThread = null
        LogUtils.info("[VoiceManager] PCM录音已停止")
    }

    fun isPlaying(filePath: String): Boolean {
        return currentPlayingPath == filePath && mediaPlayer?.isPlaying == true
    }

    // 语音播放
    fun playVoiceMessage(filePath: String) {
        if (mediaPlayer?.isPlaying == true) {
            stopPlayback()
            return
        }

        if (currentPlayingPath == filePath) {
            stopPlayback()
            return
        }
        stopPlayback()
        currentPlayingPath = filePath

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(filePath)
                prepare()
                start()
                setOnCompletionListener {
                    stopPlayback()
                    onPlaybackCompleteListener?.onPlaybackComplete(filePath)
                }
            }
            LogUtils.info("[VoiceManager] 开始播放语音: $filePath")
        } catch (e: IOException) {
            LogUtils.error("[VoiceManager] 播放失败: ${e.message}")
        }
    }

    fun stopPlayback() {
        mediaPlayer?.release()
        mediaPlayer = null
        currentPlayingPath = null
    }

    // 删除语音文件
    fun deleteVoiceFile(filePath: String): Boolean {
        return try {
            val file = File(filePath)
            if (file.exists()) {
                val success = file.delete()
                if (success) {
                    LogUtils.info("[VoiceManager] 语音文件已删除: $filePath")
                    // 如果正在播放被删除的文件，停止播放
                    if (currentPlayingPath == filePath) {
                        stopPlayback()
                    }
                } else {
                    LogUtils.error("[VoiceManager] 文件删除失败: $filePath")
                }
                success
            } else {
                LogUtils.warn("[VoiceManager] 文件不存在: $filePath")
                false
            }
        } catch (e: SecurityException) {
            LogUtils.error("[VoiceManager] 权限拒绝删除文件: ${e.message}")
            false
        }
    }


    // 资源释放
    fun release() {
        stopRecording()
        mediaPlayer?.release()
        mediaPlayer = null
        LogUtils.info("[VoiceManager] 所有资源已释放")
    }

    interface OnPlaybackCompleteListener {
        fun onPlaybackComplete(filePath: String)
    }

    var onPlaybackCompleteListener: OnPlaybackCompleteListener? = null
}