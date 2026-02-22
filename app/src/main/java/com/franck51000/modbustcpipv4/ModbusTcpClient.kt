package com.franck51000.modbustcpipv4

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Modbus TCP/IP Client implementing Modbus functions over TCP/IPv4.
 *
 * Supported function codes:
 *  - FC01: Read Coils
 *  - FC02: Read Discrete Inputs
 *  - FC03: Read Holding Registers
 *  - FC04: Read Input Registers
 *  - FC05: Write Single Coil
 *  - FC06: Write Single Register
 *  - FC15: Write Multiple Coils
 *  - FC16: Write Multiple Registers
 */
class ModbusTcpClient(
    private val host: String,
    private val port: Int = 502,
    private val unitId: Int = 1,
    private val timeoutMs: Int = 3000
) {
    private var socket: Socket? = null
    private var transactionId: Int = 0

    val isConnected: Boolean
        get() = socket?.isConnected == true && socket?.isClosed == false

    @Throws(IOException::class)
    fun connect() {
        socket = Socket()
        socket!!.connect(InetSocketAddress(host, port), timeoutMs)
        socket!!.soTimeout = timeoutMs
    }

    fun disconnect() {
        try { socket?.close() } catch (_: Exception) {}
        socket = null
    }

    private fun nextTransactionId(): Int {
        transactionId = (transactionId + 1) and 0xFFFF
        return transactionId
    }

    private fun buildMbapRequest(pdu: ByteArray): ByteArray {
        val tid = nextTransactionId()
        val length = pdu.size + 1 // PDU + unitId
        return byteArrayOf(
            (tid shr 8).toByte(), tid.toByte(),
            0, 0,
            (length shr 8).toByte(), length.toByte(),
            unitId.toByte()
        ) + pdu
    }

    private fun sendAndReceive(pdu: ByteArray): ByteArray {
        val request = buildMbapRequest(pdu)
        val out = socket!!.getOutputStream()
        val inp = socket!!.getInputStream()
        out.write(request)
        out.flush()

        // Read MBAP header (7 bytes)
        val header = ByteArray(7)
        var read = 0
        while (read < 7) read += inp.read(header, read, 7 - read)

        val dataLength = ((header[4].toInt() and 0xFF) shl 8) or (header[5].toInt() and 0xFF)
        val payload = ByteArray(dataLength - 1) // minus unitId byte
        read = 0
        while (read < payload.size) read += inp.read(payload, read, payload.size - read)

        if (payload.isEmpty()) throw IOException("Empty response")
        if ((payload[0].toInt() and 0x80) != 0) {
            val errorCode = if (payload.size > 1) payload[1].toInt() and 0xFF else 0
            throw ModbusException("Modbus exception: FC=${payload[0].toInt() and 0x7F}, Code=$errorCode")
        }
        return payload
    }

    /** FC01 – Read Coils */
    fun readCoils(startAddress: Int, quantity: Int): List<Int> {
        val pdu = byteArrayOf(
            0x01,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (quantity shr 8).toByte(), quantity.toByte()
        )
        val response = sendAndReceive(pdu)
        return extractBits(response, quantity)
    }

    /** FC02 – Read Discrete Inputs */
    fun readDiscreteInputs(startAddress: Int, quantity: Int): List<Int> {
        val pdu = byteArrayOf(
            0x02,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (quantity shr 8).toByte(), quantity.toByte()
        )
        val response = sendAndReceive(pdu)
        return extractBits(response, quantity)
    }

    /** FC03 – Read Holding Registers */
    fun readHoldingRegisters(startAddress: Int, quantity: Int): List<Int> {
        val pdu = byteArrayOf(
            0x03,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (quantity shr 8).toByte(), quantity.toByte()
        )
        val response = sendAndReceive(pdu)
        return extractRegisters(response, quantity)
    }

    /** FC04 – Read Input Registers */
    fun readInputRegisters(startAddress: Int, quantity: Int): List<Int> {
        val pdu = byteArrayOf(
            0x04,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (quantity shr 8).toByte(), quantity.toByte()
        )
        val response = sendAndReceive(pdu)
        return extractRegisters(response, quantity)
    }

    /** FC05 – Write Single Coil */
    fun writeSingleCoil(address: Int, value: Boolean): Boolean {
        val coilValue = if (value) 0xFF00 else 0x0000
        val pdu = byteArrayOf(
            0x05,
            (address shr 8).toByte(), address.toByte(),
            (coilValue shr 8).toByte(), coilValue.toByte()
        )
        sendAndReceive(pdu)
        return true
    }

    /** FC06 – Write Single Register */
    fun writeSingleRegister(address: Int, value: Int): Boolean {
        val pdu = byteArrayOf(
            0x06,
            (address shr 8).toByte(), address.toByte(),
            (value shr 8).toByte(), value.toByte()
        )
        sendAndReceive(pdu)
        return true
    }

    /** FC15 – Write Multiple Coils */
    fun writeMultipleCoils(startAddress: Int, values: List<Boolean>): Boolean {
        val byteCount = (values.size + 7) / 8
        val coilBytes = ByteArray(byteCount)
        values.forEachIndexed { i, v -> if (v) coilBytes[i / 8] = (coilBytes[i / 8].toInt() or (1 shl (i % 8))).toByte() }
        val pdu = byteArrayOf(
            0x0F,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (values.size shr 8).toByte(), values.size.toByte(),
            byteCount.toByte()
        ) + coilBytes
        sendAndReceive(pdu)
        return true
    }

    /** FC16 – Write Multiple Registers */
    fun writeMultipleRegisters(startAddress: Int, values: List<Int>): Boolean {
        val byteCount = values.size * 2
        val regBytes = ByteArray(byteCount)
        values.forEachIndexed { i, v ->
            regBytes[i * 2] = (v shr 8).toByte()
            regBytes[i * 2 + 1] = v.toByte()
        }
        val pdu = byteArrayOf(
            0x10,
            (startAddress shr 8).toByte(), startAddress.toByte(),
            (values.size shr 8).toByte(), values.size.toByte(),
            byteCount.toByte()
        ) + regBytes
        sendAndReceive(pdu)
        return true
    }

    private fun extractBits(response: ByteArray, quantity: Int): List<Int> {
        // response[0] = FC, response[1] = byteCount, response[2..] = data
        val result = mutableListOf<Int>()
        for (i in 0 until quantity) {
            val byteIndex = i / 8 + 2
            val bitIndex = i % 8
            result.add((response[byteIndex].toInt() shr bitIndex) and 1)
        }
        return result
    }

    private fun extractRegisters(response: ByteArray, quantity: Int): List<Int> {
        // response[0] = FC, response[1] = byteCount, response[2..] = data
        val result = mutableListOf<Int>()
        for (i in 0 until quantity) {
            val hi = response[2 + i * 2].toInt() and 0xFF
            val lo = response[3 + i * 2].toInt() and 0xFF
            result.add((hi shl 8) or lo)
        }
        return result
    }
}

class ModbusException(message: String) : IOException(message)
