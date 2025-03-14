package com.example.jvsglass.utils

import kotlin.math.ceil

object PacketUtils {
    private const val HEADER_AA = 0xAA.toByte()
    private const val HEADER_55 = 0x55.toByte()
    private const val HEADER_SIZE = 5   // 5字节头部长度
    private const val ATT_HEADER_LENGTH = 3  // 3字节ATT协议头长度
    private const val SAFETY_MARGIN = 2  // 2字节安全余量

    // 统一分包方法
    fun createPackets(message: String, currentMtu: Int): List<ByteArray> {
        val messageBytes = message.toByteArray(Charsets.UTF_8)
        val packetSize = currentMtu - HEADER_SIZE - ATT_HEADER_LENGTH - SAFETY_MARGIN
        val totalPackets = ceil(messageBytes.size.toDouble() / packetSize).toInt()

        return (0 until totalPackets).map { i ->
            val start = i * packetSize
            val end = minOf(start + packetSize, messageBytes.size)
            val payloadSize = end - start

            ByteArray(HEADER_SIZE + payloadSize).apply { // 增加1字节存储payload长度
                this[0] = HEADER_AA
                this[1] = HEADER_55
                this[2] = totalPackets.toByte()
                this[3] = i.toByte()
                this[4] = payloadSize.toByte()
                System.arraycopy(messageBytes, start, this, HEADER_SIZE, payloadSize)
            }
        }
    }

    // 统一组包方法
    fun processPacket(packet: ByteArray): ProcessResult {
        return if (packet.size >= HEADER_SIZE &&
            packet[0] == HEADER_AA &&
            packet[1] == HEADER_55) {
            val total = packet[2].toInt() and 0xFF
            val index = packet[3].toInt() and 0xFF
            val payloadSize = packet[4].toInt() and 0xFF // 读取实际长度
            val payload = packet.copyOfRange(HEADER_SIZE, packet.size)
            LogUtils.debug("[BLE Server] 收到分包：$index/$total (${payload.size}字节)")
            ProcessResult.Partial(total, index, payloadSize, payload)
        } else {
            ProcessResult.Complete(String(packet, Charsets.UTF_8))
        }
    }

    sealed class ProcessResult {
        class Partial(val total: Int, val index: Int, val payloadSize: Int, val payload: ByteArray) : ProcessResult()
        class Complete(val message: String) : ProcessResult()
    }
}