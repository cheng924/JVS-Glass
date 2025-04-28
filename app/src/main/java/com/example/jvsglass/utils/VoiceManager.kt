package com.example.jvsglass.utils

import android.Manifest
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class VoiceManager(private val context: Context): MediaPlayer.OnCompletionListener {
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
    private var isPaused = false

    private var isScoOnForRecording = false // 记录是否用过蓝牙SCO录音

    interface AudioRecordCallback {
        fun onAudioData(data: ByteArray) // 实时返回音频数据块
    }

    fun setOnPlaybackCompleteListener(listener: OnPlaybackCompleteListener) {
        this.onPlaybackCompleteListener = listener
    }

    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    interface OnPlaybackCompleteListener {
        fun onPlaybackComplete(filePath: String)
    }

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startStreaming(isPhoneMic: Boolean, callback: AudioRecordCallback) {
        if (isPhoneMic) {
            // 关闭蓝牙SCO录音
            if (isScoOnForRecording) {
                audioManager.stopBluetoothSco()
                audioManager.isBluetoothScoOn = false
                isScoOnForRecording = false
            }
        } else {
            // 开启蓝牙SCO录音
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            isScoOnForRecording = true
        }

        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        audioRecord = AudioRecord(
            if (isPhoneMic) {MediaRecorder.AudioSource.MIC} else {MediaRecorder.AudioSource.VOICE_COMMUNICATION},
            sampleRate,
            channelConfig,
            audioFormat,
            bufferSize * 2
        )

        audioRecord!!.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            while (isRecording) {
                val bytesRead = audioRecord!!.read(buffer, 0, bufferSize)
                if (bytesRead > 0) {
                    callback.onAudioData(buffer.copyOf(bytesRead))
                }
            }
        }.apply { start() }
    }

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

    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun startStreamingAndRecording(callback: AudioRecordCallback): String? {
        val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
        val timestamp = SimpleDateFormat("yyyyMMddHHmmss", Locale.getDefault()).format(Date())
        val fileName = "$timestamp.pcm"
        val outputFile = File(context.getExternalFilesDir(null), fileName)

        audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate, channelConfig, audioFormat, bufferSize * 2
        )
        audioRecord!!.startRecording()
        isRecording = true

        recordingThread = Thread {
            val buffer = ByteArray(bufferSize)
            FileOutputStream(outputFile).use { fos ->
                while (isRecording) {
                    val bytesRead = audioRecord!!.read(buffer, 0, bufferSize)
                    if (bytesRead > 0) {
                        val audioData = buffer.copyOf(bytesRead)
                        callback.onAudioData(audioData)
                        fos.write(audioData, 0, bytesRead)
                    }
                }
            }
        }.apply { start() }
        currentAudioPath = outputFile.absolutePath
        LogUtils.info("[VoiceManager] 开始流式+文件录音: $currentAudioPath")
        return currentAudioPath
    }

    fun stopRecording() {
        if (isScoOnForRecording) {
            // 停止录音后关闭SCO
            audioManager.stopBluetoothSco()
            audioManager.isBluetoothScoOn = false
            isScoOnForRecording = false
        }

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

    // 语音播放
    fun playVoiceMessage(filePath: String) {
        LogUtils.info("[VoiceManager] 播放语音: $filePath")
        if (currentPlayingPath == filePath && isPaused) {
            resumePlayback()
            return
        }

        if (currentPlayingPath == filePath && mediaPlayer?.isPlaying == true) {
            stopPlayback()
            return
        }

        stopPlayback()
        currentPlayingPath = filePath
        isPaused = false
        if (filePath.endsWith(".pcm")) {
            pcmToWav(filePath)?.let { wav ->
                currentPlayingPath = wav
                LogUtils.info("[VoiceManager] 已转换为 WAV: $wav")
            }
        }

        try {
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentPlayingPath)
                prepare()
                start()
                setOnCompletionListener(this@VoiceManager)
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
        isPaused = false
        LogUtils.info("[VoiceManager] 播放已停止")
    }

    // 暂停播放
    fun pausePlayback() {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPaused = true
                LogUtils.info("[VoiceManager] 播放已暂停")
            }
        }
    }

    // 继续播放
    private fun resumePlayback() {
        mediaPlayer?.let {
            if (isPaused) {
                it.start()
                isPaused = false
                LogUtils.info("[VoiceManager] 播放已继续")
            }
        }
    }

    override fun onCompletion(mp: MediaPlayer) {
        stopPlayback()
        onPlaybackCompleteListener?.onPlaybackComplete(currentPlayingPath ?: "")
    }

    // 将 PCM 原始数据转为 WAV 文件，并返回新 WAV 路径
    private fun pcmToWav(pcmPath: String): String? {
        val pcmFile = File(pcmPath)
        if (!pcmFile.exists()) return null

        val wavFile = File(pcmFile.parent, pcmFile.nameWithoutExtension + ".wav")
        try {
            FileInputStream(pcmFile).use { `in` ->
                FileOutputStream(wavFile).use { out ->
                    val totalAudioLen = pcmFile.length()
                    val totalDataLen = totalAudioLen + 36
                    val channels = 1
                    val byteRate = 16 * sampleRate * channels / 8

                    // 写 WAV header
                    val header = ByteArray(44)
                    // ChunkID "RIFF"
                    System.arraycopy("RIFF".toByteArray(), 0, header, 0, 4)
                    // ChunkSize
                    ByteBuffer.wrap(header, 4, 4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt((totalDataLen).toInt())
                    // Format "WAVE"
                    System.arraycopy("WAVE".toByteArray(), 0, header, 8, 4)
                    // Subchunk1ID "fmt "
                    System.arraycopy("fmt ".toByteArray(), 0, header, 12, 4)
                    // Subchunk1Size (16 for PCM)
                    ByteBuffer.wrap(header, 16, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(16)
                    // AudioFormat (1 for PCM)
                    ByteBuffer.wrap(header, 20, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(1)
                    // NumChannels
                    ByteBuffer.wrap(header, 22, 2).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort(channels.toShort())
                    // SampleRate
                    ByteBuffer.wrap(header, 24, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(sampleRate)
                    // ByteRate
                    ByteBuffer.wrap(header, 28, 4).order(ByteOrder.LITTLE_ENDIAN).putInt(byteRate)
                    // BlockAlign = NumChannels * BitsPerSample/8
                    ByteBuffer.wrap(header, 32, 2).order(ByteOrder.LITTLE_ENDIAN)
                        .putShort((channels * 16 / 8).toShort())
                    // BitsPerSample
                    ByteBuffer.wrap(header, 34, 2).order(ByteOrder.LITTLE_ENDIAN).putShort(16)
                    // Subchunk2ID "data"
                    System.arraycopy("data".toByteArray(), 0, header, 36, 4)
                    // Subchunk2Size
                    ByteBuffer.wrap(header, 40, 4).order(ByteOrder.LITTLE_ENDIAN)
                        .putInt(totalAudioLen.toInt())

                    out.write(header)
                    // 写 PCM 数据
                    val buffer = ByteArray(1024)
                    var bytesRead: Int
                    while (`in`.read(buffer).also { bytesRead = it } != -1) {
                        out.write(buffer, 0, bytesRead)
                    }
                    out.flush()
                }
            }
            return wavFile.absolutePath
        } catch (e: IOException) {
            e.printStackTrace()
            return null
        }
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

    private var onPlaybackCompleteListener: OnPlaybackCompleteListener? = null
}