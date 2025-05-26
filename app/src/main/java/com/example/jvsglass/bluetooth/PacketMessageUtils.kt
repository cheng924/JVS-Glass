package com.example.jvsglass.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketMessageUtils {
    /**********
     * 新协议结构：
     * [0] BLE头 0x01
     * [1] 指令 0x04
     * [2] TLV头 0x80
     * [3-4] TLV数据长度（小端）
     * [5+] 嵌套的文本TLV块：
     *      [0] 文本头 0x01
     *      [1-2] 文本长度（小端）
     *      [3+] 实际UTF-8文本数据
     **********/

    // ================== 协议常量 ==================
    private const val BLE_HEADER = 0x01.toByte()
    private const val BLE_COMMAND = 0x04.toByte()
    private const val TLV_HEADER = 0x80.toByte()
    private const val TEXT_HEADER = 0x01.toByte()

    // ================== 创建数据包 ==================
    fun createPacket(message: String): ByteArray {
        val textData = message.toByteArray(Charsets.UTF_8)
        val textBlock = buildTextBlock(textData)
        return assemblePacket(textBlock)
    }

    // ============== 私有构建方法 ==============
    private fun buildTextBlock(textData: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(3 + textData.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(TEXT_HEADER)
        buffer.putShort(textData.size.toShort())
        buffer.put(textData)
        return buffer.array()
    }

    private fun assemblePacket(textBlock: ByteArray): ByteArray {
        val tlvDataLength = textBlock.size
        val buffer = ByteBuffer.allocate(5 + tlvDataLength).order(ByteOrder.LITTLE_ENDIAN)
        buffer.put(BLE_HEADER)
        buffer.put(BLE_COMMAND)
        buffer.put(TLV_HEADER)
        buffer.putShort(tlvDataLength.toShort())
        buffer.put(textBlock)
        return buffer.array()
    }

    // ================== 解析数据包 ==================
    fun processPacket(packet: ByteArray): String {
        // 验证基础协议头
        if (packet.size < 5 ||
            packet[0] != BLE_HEADER ||
            packet[1] != BLE_COMMAND ||
            packet[2] != TLV_HEADER
        ) {
            throw IllegalArgumentException("[协议错误] 无效包头")
        }

        // 解析TLV数据长度（小端）
        val tlvDataLength = ByteBuffer.wrap(packet, 3, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

        // 验证数据完整性
        if (packet.size < 5 + tlvDataLength) {
            throw IllegalArgumentException("[数据错误] TLV数据不完整")
        }

        // 提取TLV数据部分
        val tlvData = packet.copyOfRange(5, 5 + tlvDataLength)

        // 验证文本块
        if (tlvData.isEmpty() || tlvData[0] != TEXT_HEADER) {
            throw IllegalArgumentException("[协议错误] 无效文本块")
        }

        // 解析文本长度
        val textLength = ByteBuffer.wrap(tlvData, 1, 2).order(ByteOrder.LITTLE_ENDIAN).short.toInt()

        // 提取最终文本数据
        val textData = tlvData.copyOfRange(3, 3 + textLength)
        return String(textData, Charsets.UTF_8)
    }
}