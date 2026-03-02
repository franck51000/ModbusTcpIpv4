package com.franck51000.modbustcpipv4

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var modbusClient: ModbusTcpClient? = null
    @Volatile private var cyclicRunning = false
    @Volatile private var cyclicThread: Thread? = null
    private var displayFormat = DisplayFormat.DECIMAL

    enum class DisplayFormat { DECIMAL, HEXADECIMAL, BINARY }

    // View references
    private lateinit var editIpAddress: EditText
    private lateinit var editPort: EditText
    private lateinit var editUnitId: EditText
    private lateinit var editStartAddress: EditText
    private lateinit var editNumRegisters: EditText
    private lateinit var spinnerFunctionCode: Spinner
    private lateinit var editWriteValue: EditText
    private lateinit var btnConnect: Button
    private lateinit var btnRead: Button
    private lateinit var btnWrite: Button
    private lateinit var checkboxCyclic: CheckBox
    private lateinit var editCyclicInterval: EditText
    private lateinit var radioGroupFormat: RadioGroup
    private lateinit var textLog: TextView
    private lateinit var scrollLog: ScrollView
    private lateinit var tableRegisters: TableLayout

    private val functionCodes = listOf(
        "FC03 - Lire Registres Holding",
        "FC01 - Lire Bobines",
        "FC02 - Lire Entrées Discretes",
        "FC04 - Lire Registres Entrée",
        "FC05 - Ecrire Bobine",
        "FC06 - Ecrire Registre",
        "FC15 - Ecrire Multiples Bobines",
        "FC16 - Ecrire Multiples Registres"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editIpAddress = findViewById(R.id.editIpAddress)
        editPort = findViewById(R.id.editPort)
        editUnitId = findViewById(R.id.editUnitId)
        editStartAddress = findViewById(R.id.editStartAddress)
        editNumRegisters = findViewById(R.id.editNumRegisters)
        spinnerFunctionCode = findViewById(R.id.spinnerFunctionCode)
        editWriteValue = findViewById(R.id.editWriteValue)
        btnConnect = findViewById(R.id.btnConnect)
        btnRead = findViewById(R.id.btnRead)
        btnWrite = findViewById(R.id.btnWrite)
        checkboxCyclic = findViewById(R.id.checkboxCyclic)
        editCyclicInterval = findViewById(R.id.editCyclicInterval)
        radioGroupFormat = findViewById(R.id.radioGroupFormat)
        textLog = findViewById(R.id.textLog)
        scrollLog = findViewById(R.id.scrollLog)
        tableRegisters = findViewById(R.id.tableRegisters)

        setupFunctionCodeSpinner()
        setupFormatRadioGroup()
        setupButtons()
        updateConnectionUI(false)
    }

    private fun setupFunctionCodeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, functionCodes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerFunctionCode.adapter = adapter
    }

    private fun setupFormatRadioGroup() {
        radioGroupFormat.setOnCheckedChangeListener { _, checkedId ->
            displayFormat = when (checkedId) {
                R.id.radioDecimal -> DisplayFormat.DECIMAL
                R.id.radioHexadecimal -> DisplayFormat.HEXADECIMAL
                R.id.radioBinary -> DisplayFormat.BINARY
                else -> DisplayFormat.DECIMAL
            }
        }
    }

    private fun setupButtons() {
        btnConnect.setOnClickListener { toggleConnection() }
        btnRead.setOnClickListener { performRead() }
        btnWrite.setOnClickListener { performWrite() }
        checkboxCyclic.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) startCyclicMode() else stopCyclicMode()
        }
    }

    private fun toggleConnection() {
        if (modbusClient?.isConnected == true) {
            disconnectFromDevice()
        } else {
            connectToDevice()
        }
    }

    private fun connectToDevice() {
        val host = editIpAddress.text.toString().trim()
        val port = editPort.text.toString().toIntOrNull() ?: 502
        val unitId = editUnitId.text.toString().toIntOrNull() ?: 1

        if (host.isEmpty()) {
            appendLog("Erreur: Adresse IP requise")
            return
        }

        appendLog("Connexion à $host:$port (Unit=$unitId)...")
        Thread {
            try {
                val client = ModbusTcpClient(host, port, unitId)
                client.connect()
                modbusClient = client
                handler.post {
                    updateConnectionUI(true)
                    appendLog("Connecté à $host:$port")
                }
            } catch (e: Exception) {
                handler.post {
                    appendLog("Erreur connexion: ${e.message}")
                    modbusClient = null
                    updateConnectionUI(false)
                }
            }
        }.start()
    }

    private fun disconnectFromDevice() {
        stopCyclicMode()
        Thread {
            modbusClient?.disconnect()
            modbusClient = null
            handler.post {
                updateConnectionUI(false)
                appendLog("Déconnecté")
            }
        }.start()
    }

    private fun updateConnectionUI(connected: Boolean) {
        btnConnect.text = if (connected) "Déconnecter" else "Connecter"
        btnConnect.setBackgroundColor(
            if (connected) Color.parseColor("#F44336") else Color.parseColor("#4CAF50")
        )
        btnRead.isEnabled = connected
        btnWrite.isEnabled = connected
        checkboxCyclic.isEnabled = connected
        if (!connected) {
            checkboxCyclic.isChecked = false
        }
    }

    private fun performRead() {
        val startAddress = editStartAddress.text.toString().toIntOrNull() ?: 0
        val quantity = editNumRegisters.text.toString().toIntOrNull() ?: 1
        val fcIndex = spinnerFunctionCode.selectedItemPosition

        Thread {
            val client = modbusClient
            if (client == null || !client.isConnected) {
                handler.post { appendLog("Erreur: non connecté") }
                return@Thread
            }
            try {
                val values = when (fcIndex) {
                    0 -> client.readHoldingRegisters(startAddress, quantity)
                    1 -> client.readCoils(startAddress, quantity)
                    2 -> client.readDiscreteInputs(startAddress, quantity)
                    3 -> client.readInputRegisters(startAddress, quantity)
                    else -> throw IllegalArgumentException("Fonction non valide pour lecture")
                }
                handler.post {
                    appendLog("Lecture OK: ${values.size} valeur(s)")
                    displayRegisterValues(startAddress, values)
                }
            } catch (e: Exception) {
                handler.post { appendLog("Erreur lecture: ${e.message}") }
            }
        }.start()
    }

    private fun performWrite() {
        val startAddress = editStartAddress.text.toString().toIntOrNull() ?: 0
        val fcIndex = spinnerFunctionCode.selectedItemPosition
        val rawValue = editWriteValue.text.toString().trim()

        Thread {
            val client = modbusClient
            if (client == null || !client.isConnected) {
                handler.post { appendLog("Erreur: non connecté") }
                return@Thread
            }
            try {
                when (fcIndex) {
                    4 -> {
                        val v = rawValue.toIntOrNull() ?: 0
                        client.writeSingleCoil(startAddress, v != 0)
                    }
                    5 -> {
                        val v = parseValue(rawValue)
                        client.writeSingleRegister(startAddress, v)
                    }
                    6 -> {
                        val values = rawValue.split(",").map { it.trim().toIntOrNull() ?: 0 }.map { it != 0 }
                        client.writeMultipleCoils(startAddress, values)
                    }
                    7 -> {
                        val values = rawValue.split(",").map { parseValue(it.trim()) }
                        client.writeMultipleRegisters(startAddress, values)
                    }
                    else -> throw IllegalArgumentException("Fonction non valide pour écriture")
                }
                handler.post { appendLog("Ecriture OK à l'adresse $startAddress") }
            } catch (e: Exception) {
                handler.post { appendLog("Erreur écriture: ${e.message}") }
            }
        }.start()
    }

    private fun parseValue(s: String): Int {
        return when {
            s.startsWith("0x", ignoreCase = true) -> s.substring(2).toInt(16)
            s.startsWith("0b", ignoreCase = true) -> s.substring(2).toInt(2)
            else -> s.toIntOrNull() ?: 0
        }
    }

    private fun startCyclicMode() {
        val intervalMs = editCyclicInterval.text.toString().toLongOrNull() ?: 1000L
        cyclicRunning = true
        cyclicThread = Thread {
            while (cyclicRunning && modbusClient?.isConnected == true) {
                performRead()
                try { Thread.sleep(intervalMs) } catch (_: InterruptedException) { break }
            }
        }.also { it.start() }
        appendLog("Mode cyclique démarré (intervalle: ${intervalMs}ms)")
    }

    private fun stopCyclicMode() {
        cyclicRunning = false
        cyclicThread?.interrupt()
        cyclicThread = null
    }

    private fun formatValue(value: Int): String {
        return when (displayFormat) {
            DisplayFormat.DECIMAL -> value.toString()
            DisplayFormat.HEXADECIMAL -> "0x${value.toString(16).uppercase().padStart(4, '0')}"
            DisplayFormat.BINARY -> "0b${value.toString(2).padStart(16, '0')}"
        }
    }

    private fun displayRegisterValues(startAddress: Int, values: List<Int>) {
        tableRegisters.removeAllViews()

        val headerRow = TableRow(this)
        headerRow.setBackgroundColor(Color.parseColor("#1976D2"))
        addCell(headerRow, "Adresse", Color.WHITE, true)
        addCell(headerRow, "Valeur", Color.WHITE, true)
        tableRegisters.addView(headerRow)

        values.forEachIndexed { index, value ->
            val row = TableRow(this)
            row.setBackgroundColor(if (index % 2 == 0) Color.parseColor("#E3F2FD") else Color.WHITE)
            addCell(row, (startAddress + index).toString(), Color.BLACK, false)
            addCell(row, formatValue(value), Color.parseColor("#1565C0"), false)
            tableRegisters.addView(row)
        }
    }

    private fun addCell(row: TableRow, text: String, textColor: Int, bold: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(textColor)
        tv.setPadding(16, 8, 16, 8)
        if (bold) tv.setTypeface(null, Typeface.BOLD)
        val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        tv.layoutParams = params
        row.addView(tv)
    }

    private fun appendLog(message: String) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        val newText = "[$timestamp] $message\n"
        textLog.append(newText)
        scrollLog.post { scrollLog.fullScroll(View.FOCUS_DOWN) }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCyclicMode()
        modbusClient?.disconnect()
    }
}
