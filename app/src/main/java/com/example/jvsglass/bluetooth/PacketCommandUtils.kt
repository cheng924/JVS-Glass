package com.example.jvsglass.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketCommandUtils {
    /** cmd **/
    const val SWITCH_INTERFACE_COMMAND: Byte = 0x05.toByte()    // 打开界面

    /** value **/
    val ENTER_HOME_VALUE = byteArrayOf(0x01, 0x02, 0x00, 0x32, 0x00)          // 打开首页
    val ENTER_TELEPROMPTER_VALUE = byteArrayOf(0x01, 0x02, 0x00, 0x33, 0x00)  // 打开提词

    /** 协议固定头部 */
    private const val HEADER: Byte = 0x01.toByte()
    /** 外层 TLV 类型：固定 0x80 */
    private const val OUTER_TLV_TYPE: Byte = 0x80.toByte()

    /** 内层 TLV 对象 */
    @Suppress("ArrayInDataClass")
    data class Tlv(val type: Byte, val value: ByteArray)

    /** 解包结果 */
    data class ParsedPacket(
        val cmd: Byte,
        val tlvs: List<Tlv>
    )

    /**
     * 组包：
     *  header(1B) + cmd(1B) + outer_tlv_type(1B=0x80)
     *    + tlv_len(2B LE) + tlv_value(XB)
     *
     * @param cmd  操作命令
     * @param tlvValue 外层 TLV 的原始 value 字段（比如打开界面的那几字节）
     */
    fun createPacket(cmd: Byte, tlvValue: ByteArray): ByteArray {
        val totalLen = tlvValue.size
        val buf = ByteBuffer
            .allocate(1 + 1 + 1 + 2 + totalLen)
            .order(ByteOrder.LITTLE_ENDIAN)

        buf.put(HEADER)
        buf.put(cmd)
        buf.put(OUTER_TLV_TYPE)
        buf.putShort(totalLen.toShort())
        buf.put(tlvValue)

        return buf.array()
    }

    /**
     * 解包：
     * 1) 校验 header = 0x01
     * 2) 读 cmd
     * 3) 跳过 outer_tlv_type + length
     * 4) 拆 inner TLV 列表：type(1B)+len(2B LE)+value
     */
    fun parsePacket(packet: ByteArray): ParsedPacket? {
        if (packet.size < 5 || packet[0] != HEADER) return null

        // ByteBuffer 简化读取
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(1)
        val cmd = buf.get()
        val outerTlvType = buf.get()
        val innerLen = buf.short.toInt() and 0xFFFF

        // 剩余字节要至少和 innerLen 匹配
        if (packet.size < 1 + 1 + 1 + 2 + innerLen) return null

        // 读出 inner TLV 区
        val inner = ByteArray(innerLen)
        buf.get(inner)

        // 解析 inner TLV 列表
        val tlvs = mutableListOf<Tlv>()
        var idx = 0
        while (idx + 3 <= innerLen) {
            val type = inner[idx]
            val len = ((inner[idx + 2].toInt() and 0xFF) shl 8) or
                    (inner[idx + 1].toInt() and 0xFF)
            if (idx + 3 + len > innerLen) break

            val value = inner.copyOfRange(idx + 3, idx + 3 + len)
            tlvs += Tlv(type, value)
            idx += 3 + len
        }

        return ParsedPacket(cmd, tlvs)
    }
}