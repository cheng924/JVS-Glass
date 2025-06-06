package com.example.jvsglass.bluetooth

import java.nio.ByteBuffer
import java.nio.ByteOrder

object PacketCommandUtils {
    object RemoteControlKeyValue {
        const val KEY_NULL = 0      // NULL
        const val KEY_NEXT = 1      // 下
        const val KEY_PREV = 2      // 上
        const val KEY_ENTER = 3     // 确定
        const val KEY_RIGHT = 4     // 右
        const val KEY_LEFT = 5      // 左
        const val KEY_ESC = 6       // 返回
        const val KEY_ERROR = -1    // 错误
    }

    /** cmd **/
    const val SWITCH_INTERFACE_COMMAND: Byte = 0x05.toByte()    // 打开界面

    /** value **/
    val ENTER_HOME_VALUE = byteArrayOf(0x01, 0x02, 0x00, 0x32, 0x00)          // 打开首页
    val ENTER_TELEPROMPTER_VALUE = byteArrayOf(0x01, 0x02, 0x00, 0x33, 0x00)  // 打开提词

    /** 协议固定头部 */
    private const val HEADER: Byte = 0x01.toByte()
    /** 外层 TLV 类型 */
    private const val OUTER_TLV_TYPE: Byte = 0x80.toByte()
    private const val CMD_SEND_MESSAGE_REMINDER: Byte = 0x03.toByte()

    /** 内层 TLV 类型定义 */
    private const val TLV_MSG_NAME: Byte  = 0x01.toByte()
    private const val TLV_MSG_TITLE: Byte = 0x02.toByte()
    private const val TLV_MSG_TEXT: Byte  = 0x03.toByte()
    private const val TLV_MSG_DATE: Byte  = 0x04.toByte()

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
     *  header(1B) + cmd(1B) + outer_tlv_type(1B=0x80) + tlv_len(2B LE) + tlv_value(XB)
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
    private fun parsePacket(packet: ByteArray): ParsedPacket? {
        if (packet.size < 5 || packet[0] != HEADER) return null

        // ByteBuffer 简化读取
        val buf = ByteBuffer.wrap(packet).order(ByteOrder.LITTLE_ENDIAN)
        buf.position(1)
        val cmd = buf.get()
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

    /**
     * 解析按键事件：提取 key_value 值
     * cmd = 0x82，inner TLV 类型为 0x01
     */
    fun parseKeyValuePacket(packet: ByteArray): Int {
        val parsed = parsePacket(packet) ?: return RemoteControlKeyValue.KEY_ERROR
        if (parsed.cmd != 0x82.toByte()) return RemoteControlKeyValue.KEY_ERROR

        val keyTlv = parsed.tlvs.find { it.type == 0x01.toByte() } ?: return RemoteControlKeyValue.KEY_ERROR
        if (keyTlv.value.size != 1) return RemoteControlKeyValue.KEY_ERROR

        return when (keyTlv.value[0].toInt() and 0xFF) {
            0x00 -> RemoteControlKeyValue.KEY_NULL
            0x09 -> RemoteControlKeyValue.KEY_NEXT
            0x0B -> RemoteControlKeyValue.KEY_PREV
            0X0A -> RemoteControlKeyValue.KEY_ENTER
            0X13 -> RemoteControlKeyValue.KEY_RIGHT
            0X14 -> RemoteControlKeyValue.KEY_LEFT
            0X1B -> RemoteControlKeyValue.KEY_ESC
            else -> RemoteControlKeyValue.KEY_ERROR
        }
    }

    /**
     * 通用 TLV 构造器
     * @param type 1B：TLV 类型
     * @param value XB：字段内容（UTF-8 字节数组）
     * @return 完整的 [type|length(2B LE)|value] 字节数组
     */
    private fun buildTlv(type: Byte, value: ByteArray): ByteArray {
        val buf = ByteBuffer
            .allocate(1 + 2 + value.size)
            .order(ByteOrder.LITTLE_ENDIAN)
        buf.put(type)
        buf.putShort(value.size.toShort())
        buf.put(value)
        return buf.array()
    }

    /**
     * 消息提醒 - 发送包
     *
     * 内层 TLV 顺序不限，可缺：
     *   • TLV_MSG_NAME
     *   • TLV_MSG_TITLE
     *   • TLV_MSG_TEXT
     *   • TLV_MSG_DATE
     *
     * @param name   发送人或来源名称
     * @param title  消息标题
     * @param text   消息正文
     * @param date   时间戳字符串（"yyyy-MM-dd HH:mm:ss"）
     */
    fun createMessageReminderPacket(
        name: String,
        title: String,
        text: String,
        date: String
    ): ByteArray {
        val nameBytes  = name.toByteArray(Charsets.UTF_8)
        val titleBytes = title.toByteArray(Charsets.UTF_8)
        val textBytes  = text.toByteArray(Charsets.UTF_8)
        val dateBytes  = date.toByteArray(Charsets.UTF_8)

        val tlvName  = buildTlv(TLV_MSG_NAME,  nameBytes)
        val tlvTitle = buildTlv(TLV_MSG_TITLE, titleBytes)
        val tlvText  = buildTlv(TLV_MSG_TEXT,  textBytes)
        val tlvDate  = buildTlv(TLV_MSG_DATE,  dateBytes)

        val innerTlv = tlvName + tlvTitle + tlvText + tlvDate
        return createPacket(CMD_SEND_MESSAGE_REMINDER, innerTlv)
    }
}