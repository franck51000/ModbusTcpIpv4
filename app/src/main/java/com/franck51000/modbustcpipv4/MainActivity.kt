package com.franck51000.modbustcpipv4

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.franck51000.modbustcpipv4.databinding.ActivityMainBinding
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var modbusClient: ModbusTcpClient? = null
    private var cyclicJob: Job? = null
    private var displayFormat = DisplayFormat.DECIMAL

    enum class DisplayFormat { DECIMAL, HEXADECIMAL, BINARY }

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
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupFunctionCodeSpinner()
        setupFormatRadioGroup()
        setupButtons()
        updateConnectionUI(false)
    }

    private fun setupFunctionCodeSpinner() {
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, functionCodes)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFunctionCode.adapter = adapter
    }

    private fun setupFormatRadioGroup() {
        binding.radioGroupFormat.setOnCheckedChangeListener { _, checkedId ->
            displayFormat = when (checkedId) {
                R.id.radioDecimal -> DisplayFormat.DECIMAL
                R.id.radioHexadecimal -> DisplayFormat.HEXADECIMAL
                R.id.radioBinary -> DisplayFormat.BINARY
                else -> DisplayFormat.DECIMAL
            }
        }
    }

    private fun setupButtons() {
        binding.btnConnect.setOnClickListener { toggleConnection() }
        binding.btnRead.setOnClickListener { performRead() }
        binding.btnWrite.setOnClickListener { performWrite() }
        binding.checkboxCyclic.setOnCheckedChangeListener { _, isChecked ->
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
        val host = binding.editIpAddress.text.toString().trim()
        val port = binding.editPort.text.toString().toIntOrNull() ?: 502
        val unitId = binding.editUnitId.text.toString().toIntOrNull() ?: 1

        if (host.isEmpty()) {
            appendLog("Erreur: Adresse IP requise")
            return
        }

        lifecycleScope.launch {
            appendLog("Connexion à $host:$port (Unit=$unitId)...")
            try {
                withContext(Dispatchers.IO) {
                    modbusClient = ModbusTcpClient(host, port, unitId)
                    modbusClient!!.connect()
                }
                updateConnectionUI(true)
                appendLog("Connecté à $host:$port")
            } catch (e: Exception) {
                appendLog("Erreur connexion: ${e.message}")
                modbusClient = null
                updateConnectionUI(false)
            }
        }
    }

    private fun disconnectFromDevice() {
        stopCyclicMode()
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                modbusClient?.disconnect()
                modbusClient = null
            }
            updateConnectionUI(false)
            appendLog("Déconnecté")
        }
    }

    private fun updateConnectionUI(connected: Boolean) {
        binding.btnConnect.text = if (connected) "Déconnecter" else "Connecter"
        binding.btnConnect.setBackgroundColor(if (connected) Color.parseColor("#F44336") else Color.parseColor("#4CAF50"))
        binding.btnRead.isEnabled = connected
        binding.btnWrite.isEnabled = connected
        binding.checkboxCyclic.isEnabled = connected
        if (!connected) {
            binding.checkboxCyclic.isChecked = false
        }
    }

    private fun performRead() {
        val startAddress = binding.editStartAddress.text.toString().toIntOrNull() ?: 0
        val quantity = binding.editNumRegisters.text.toString().toIntOrNull() ?: 1
        val fcIndex = binding.spinnerFunctionCode.selectedItemPosition

        lifecycleScope.launch {
            try {
                val values = withContext(Dispatchers.IO) {
                    when (fcIndex) {
                        0 -> modbusClient!!.readHoldingRegisters(startAddress, quantity)
                        1 -> modbusClient!!.readCoils(startAddress, quantity)
                        2 -> modbusClient!!.readDiscreteInputs(startAddress, quantity)
                        3 -> modbusClient!!.readInputRegisters(startAddress, quantity)
                        else -> throw IllegalArgumentException("Fonction non valide pour lecture")
                    }
                }
                appendLog("Lecture OK: ${values.size} valeur(s)")
                displayRegisterValues(startAddress, values)
            } catch (e: Exception) {
                appendLog("Erreur lecture: ${e.message}")
            }
        }
    }

    private fun performWrite() {
        val startAddress = binding.editStartAddress.text.toString().toIntOrNull() ?: 0
        val fcIndex = binding.spinnerFunctionCode.selectedItemPosition
        val rawValue = binding.editWriteValue.text.toString().trim()

        lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    when (fcIndex) {
                        4 -> {
                            val v = rawValue.toIntOrNull() ?: 0
                            modbusClient!!.writeSingleCoil(startAddress, v != 0)
                        }
                        5 -> {
                            val v = parseValue(rawValue)
                            modbusClient!!.writeSingleRegister(startAddress, v)
                        }
                        6 -> {
                            val values = rawValue.split(",").map { it.trim().toIntOrNull() ?: 0 }.map { it != 0 }
                            modbusClient!!.writeMultipleCoils(startAddress, values)
                        }
                        7 -> {
                            val values = rawValue.split(",").map { parseValue(it.trim()) }
                            modbusClient!!.writeMultipleRegisters(startAddress, values)
                        }
                        else -> throw IllegalArgumentException("Fonction non valide pour écriture")
                    }
                }
                appendLog("Ecriture OK à l'adresse $startAddress")
            } catch (e: Exception) {
                appendLog("Erreur écriture: ${e.message}")
            }
        }
    }

    private fun parseValue(s: String): Int {
        return when {
            s.startsWith("0x", ignoreCase = true) -> s.substring(2).toInt(16)
            s.startsWith("0b", ignoreCase = true) -> s.substring(2).toInt(2)
            else -> s.toIntOrNull() ?: 0
        }
    }

    private fun startCyclicMode() {
        val intervalMs = binding.editCyclicInterval.text.toString().toLongOrNull() ?: 1000L
        cyclicJob = lifecycleScope.launch {
            while (isActive && modbusClient?.isConnected == true) {
                performRead()
                delay(intervalMs)
            }
        }
        appendLog("Mode cyclique démarré (intervalle: ${intervalMs}ms)")
    }

    private fun stopCyclicMode() {
        cyclicJob?.cancel()
        cyclicJob = null
        appendLog("Mode cyclique arrêté")
    }

    private fun formatValue(value: Int): String {
        return when (displayFormat) {
            DisplayFormat.DECIMAL -> value.toString()
            DisplayFormat.HEXADECIMAL -> "0x${value.toString(16).uppercase().padStart(4, '0')}"
            DisplayFormat.BINARY -> "0b${value.toString(2).padStart(16, '0')}"
        }
    }

    private fun displayRegisterValues(startAddress: Int, values: List<Int>) {
        binding.tableRegisters.removeAllViews()

        // Header row
        val headerRow = TableRow(this)
        headerRow.setBackgroundColor(Color.parseColor("#1976D2"))
        addCell(headerRow, "Adresse", Color.WHITE, true)
        addCell(headerRow, "Valeur", Color.WHITE, true)
        binding.tableRegisters.addView(headerRow)

        values.forEachIndexed { index, value ->
            val row = TableRow(this)
            row.setBackgroundColor(if (index % 2 == 0) Color.parseColor("#E3F2FD") else Color.WHITE)
            addCell(row, (startAddress + index).toString(), Color.BLACK, false)
            addCell(row, formatValue(value), Color.parseColor("#1565C0"), false)
            binding.tableRegisters.addView(row)
        }
    }

    private fun addCell(row: TableRow, text: String, textColor: Int, bold: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextColor(textColor)
        tv.setPadding(16, 8, 16, 8)
        if (bold) tv.setTypeface(null, android.graphics.Typeface.BOLD)
        val params = TableRow.LayoutParams(0, TableRow.LayoutParams.WRAP_CONTENT, 1f)
        tv.layoutParams = params
        row.addView(tv)
    }

    private fun appendLog(message: String) {
        val timestamp = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())
        val newText = "[$timestamp] $message\n"
        binding.textLog.append(newText)
        // Auto-scroll
        binding.scrollLog.post {
            binding.scrollLog.fullScroll(View.FOCUS_DOWN)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCyclicMode()
        modbusClient?.disconnect()
    }
}
