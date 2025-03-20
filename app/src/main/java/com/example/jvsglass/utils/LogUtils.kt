package com.example.jvsglass.utils

import android.annotation.SuppressLint
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date

/**********
基本用法
// 调试细节：输出 DEBUG 级别日志（仅在 DEBUG 模式下可见）
LogUtils.debug("xxx")

// 关键业务节点：输出 INFO 级别日志（默认级别）
LogUtils.info("xxx")

// 潜在问题预警：输出 WARN 级别日志
LogUtils.warn("xxx")

// 系统错误与异常：输出 ERROR 级别日志（带异常堆栈）
try {
    // 可能抛出异常的代码
} catch (e: IOException) {
    LogUtils.error("xxx", e)
}
**********/

object LogUtils {
    // 默认日志级别为 INFO（只输出 INFO、WARN、ERROR）
    private var logLevel = Level.INFO

    // 是否输出到文件（默认关闭）
    private var writeToFile = false

    // 日志文件路径（需用户自行配置）
    var logFilePath = "app.log"

    // 日期格式
    @SuppressLint("SimpleDateFormat")
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")

    // 添加回调接口
    interface LogCallback {
        fun onLog(message: String)
    }

    private var logCallback: LogCallback? = null

    // 设置回调方法
    fun setLogCallback(callback: LogCallback) {
        logCallback = callback
    }

    /**
     * 设置日志级别
     */
    fun setLogLevel(level: Level) {
        logLevel = level
    }

    /**
     * 控制日志文件输出
     */
    fun enableFileLogging(filePath: String? = null) {
        if (!filePath.isNullOrEmpty()) {
            val file = File(filePath)
            if (file.parentFile?.exists() == true || file.parentFile?.mkdirs() == true) {
                writeToFile = true
                logFilePath = filePath
            } else {
                writeToFile = false
                System.err.println("Invalid log directory")
            }
        } else {
            writeToFile = false // 传入 null 时关闭文件日志
        }
    }

    /**
     * 核心日志方法
     */
    private fun log(level: Level, message: String, throwable: Throwable?) {
        if (level.ordinal < logLevel.ordinal) {
            return  // 低于当前日志级别的消息不输出
        }

        // 获取调用者的类名和方法名
        val stackTrace = Thread.currentThread().stackTrace
        var className: String? = "UnknownClass"
        var methodName: String? = "UnknownMethod"
        if (stackTrace.size > 4) { // 根据调用层级调整索引
            className = stackTrace[4].className
            methodName = stackTrace[4].methodName
        }

        // 构造日志信息
        val timestamp: String = dateFormat.format(Date())
        val logMessage = String.format(
            "[%s] [Logcat - %s] - %s",
            timestamp, level, message
//            "[%s] [%s] [%s.%s] - %s",
//            timestamp, level, className, methodName, message
        )

        // 输出到控制台
        android.util.Log.println(levelToPriority(level), "APP_LOG", logMessage)
        // 添加回调通知
        logCallback?.onLog(logMessage + "\n")
        if (level == Level.ERROR && throwable != null) {
            throwable.printStackTrace(System.err)
        }

        // 输出到文件（可选）
        if (writeToFile) {
            try {
                FileWriter(logFilePath, true).use { fw ->
                    BufferedWriter(fw).use { bw ->
                        bw.write(logMessage)
                        bw.newLine()
                        if (throwable != null) {
                            bw.write("Exception: ")
                            throwable.printStackTrace(PrintWriter(bw))
                        }
                    }
                }
            } catch (e: IOException) {
                System.err.println("Failed to write log to file: " + e.message)
            }
        }
    }

    private fun levelToPriority(level: Level): Int {
        return when (level) {
            Level.DEBUG -> android.util.Log.DEBUG
            Level.INFO -> android.util.Log.INFO
            Level.WARN -> android.util.Log.WARN
            Level.ERROR -> android.util.Log.ERROR
        }
    }

    // 对外暴露的快捷方法
    fun debug(message: String) {
        log(Level.DEBUG, message, null)
    }

    fun info(message: String) {
        log(Level.INFO, message, null)
    }

    fun warn(message: String) {
        log(Level.WARN, message, null)
    }

    fun error(message: String) {
        log(Level.ERROR, message, null)
    }

    fun error(message: String, throwable: Throwable?) {
        log(Level.ERROR, message, throwable)
    }

    // 日志级别常量
    enum class Level {
        DEBUG, INFO, WARN, ERROR
    }
}