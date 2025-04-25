package com.example.jvsglass.activities.teleprompter

import android.view.MotionEvent

object SmartTextScroller {
    data class SplitResult(
        val totalLines: Int,         // 总行数
        val displayBlock: String,   // 显示的文本块
        val sendBlock: String       // 发送的文本块
    )

    private const val MAX_DISPLAY_LINES = 2

    fun splitIntoBlocks(content: String, scrollLines: Int): SplitResult {
        val lines = mutableListOf<String>()
        val buffer = StringBuilder()
        var charCount = 0
        var position = 0

        while (position < content.length) {
            val currentChar = content[position]

            // 处理换行符
            if (currentChar == '\n') {
                lines.add(buffer.toString())
                buffer.clear()
                charCount = 0
                position++
                continue
            }

            // 字符长度计算
            val charLength = when {
                isChineseCharacter(currentChar) -> 2
                isChinesePunctuation(currentChar) -> 2
                else -> 1
            }

            when {
                // 精确匹配30字符
                charCount + charLength == 30 -> {
                    buffer.append(currentChar)
                    lines.add(buffer.toString())
                    buffer.clear()
                    charCount = 0
                    position++
                }

                // 超过30字符需要分割
                charCount + charLength > 30 -> {
                    // 处理双字节字符边界
                    if ((charLength == 2) && (charCount == 29)) {
                        buffer.append(currentChar)
                        lines.add(buffer.toString())
                        buffer.clear()
                        charCount = 0
                        position++
                    } else {
                        lines.add(buffer.toString())
                        buffer.clear()
                        charCount = 0
                    }
                }

                // 正常累积字符
                else -> {
                    buffer.append(currentChar)
                    charCount += charLength
                    position++
                }
            }
        }

        // 处理剩余内容
        if (buffer.isNotEmpty()) {
            lines.add(buffer.toString())
        }

        return SplitResult(
            totalLines = lines.size,
            displayBlock = lines.drop(scrollLines).joinToString("\n"),
            sendBlock = lines.drop(scrollLines).take(MAX_DISPLAY_LINES).joinToString("\n")
        )
    }

    // 判断是否是汉字
    private fun isChineseCharacter(c: Char): Boolean {
        return c in '\u4E00'..'\u9FFF' ||
                c in '\u3400'..'\u4DBF' ||   // 扩展A
                c.toString() in "\u20000".."\u2A6DF" || // 扩展B
                c.toString() in "\u2A700".."\u2B73F" || // 扩展C
                c.toString() in "\u2B740".."\u2B81F"    // 扩展D
    }

    // 中文标点判断（常见全角标点）
    private fun isChinesePunctuation(c: Char): Boolean {
        return c in '\u3000'..'\u303F' || // CJK标点符号范围
                c in '\uFF00'..'\uFFEF' || // 全角符号
                c == '\uFE30' || c == '\uFE31' || c == '\uFE32' || c == '\uFE33' ||
                c == '\uFE34' || c == '\uFE35' || c == '\uFE36' || c == '\uFE37' ||
                c == '\uFE38' || c == '\uFE39' || c == '\uFE3A' || c == '\uFE3B' ||
                c == '\uFE3C' || c == '\uFE3D' || c == '\uFE3E' || c == '\uFE3F'
    }
}

class VerticalScrollDetector(private val callback: (deltaY: Float) -> Unit) {
    private var startY = 0f // 手势起点坐标
    private var endY = 0f   // 手势终点坐标

    // 事件处理入口
    fun handleTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                // 记录起始Y坐标
                startY = event.y
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                // 记录结束Y坐标并计算位移
                endY = event.y
                callback(endY - startY)
                return true
            }
        }
        return false
    }
}