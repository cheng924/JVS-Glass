package com.example.jvsglass.utils

object TextFormatter {
    /**
     * 格式化文件名显示规则：
     * 1.完整显示扩展名
     * 2.主文件名部分最多显示30字符（中文算2字符）
     * 3.超出时保留前段和后段内容，中间加省略号
     */
    fun formatFileName(original: String, maxChars: Int = 38): String {
        val (namePart, extPart) = splitFileName(original)
        val totalExtChars = calculateChars(extPart)

        // 可用字符数 = 总限制 - 扩展名占用字符数 - 省略号（2字符）
        val availableChars = maxChars - totalExtChars - 2

        return when {
            calculateChars(namePart) <= availableChars -> original
            else -> buildShortName(namePart, extPart, availableChars)
        }
    }

    private fun splitFileName(fileName: String): Pair<String, String> {
        val lastDotIndex = fileName.lastIndexOf(".")
        return if (lastDotIndex != -1 && lastDotIndex < fileName.length - 1) {
            fileName.substring(0, lastDotIndex) to fileName.substring(lastDotIndex)
        } else {
            fileName to ""
        }
    }

    private fun calculateChars(text: String): Int {
        return text.sumBy { char ->
            if (char.isChinese()) 2 else 1
        }
    }

    private fun buildShortName(name: String, ext: String, max: Int): String {
        var frontCount = 0
        var frontIndex = 0
        var rearCount = 0
        var rearIndex = name.length

        // 从后往前计算可用字符
        for (i in name.length - 1 downTo 0) {
            rearCount += if (name[i].isChinese()) 2 else 1
            if (rearCount > max / 2) break
            rearIndex = i
        }

        // 从前往后计算剩余可用字符
        val remain = max - rearCount
        for (i in name.indices) {
            frontCount += if (name[i].isChinese()) 2 else 1
            if (frontCount > remain) break
            frontIndex = i + 1
        }

        return "${name.substring(0, frontIndex)}…${name.substring(rearIndex)}$ext"
    }

    private fun Char.isChinese(): Boolean {
        return this.toString().matches(Regex("\\p{Script=Han}"))
    }
}