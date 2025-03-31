package com.example.jvsglass.utils

import android.content.Context
import android.media.MediaPlayer
import android.media.MediaRecorder
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceManager(private val context: Context) {
    private var mediaRecorder: MediaRecorder? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentAudioPath: String? = null
    private var currentPlayingPath: String? = null

    // 录音控制
    fun startRecording(): String? {
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val fileName = "$timestamp.3gp"
        val outputFile = File(context.externalCacheDir ?: context.cacheDir, fileName)

        return try {
            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }
            currentAudioPath = outputFile.absolutePath
            LogUtils.info("[VoiceManager] 开始录音: $currentAudioPath")
            currentAudioPath
        } catch (e: IOException) {
            LogUtils.error("[VoiceManager] 录音失败: ${e.message}")
            null
        }
    }

    fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
        }
        mediaRecorder = null
        LogUtils.info("[VoiceManager] 停止录音")
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
        mediaRecorder?.release()
        mediaPlayer?.release()
        LogUtils.info("[VoiceManager] 资源已释放")
    }

    interface OnPlaybackCompleteListener {
        fun onPlaybackComplete(filePath: String)
    }

    var onPlaybackCompleteListener: OnPlaybackCompleteListener? = null
}