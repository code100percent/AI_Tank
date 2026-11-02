package com.example.new_tank_ai.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException
import java.io.OutputStream
import java.util.UUID

sealed interface ConnectionStatus {
    object Disconnected : ConnectionStatus
    object Connecting : ConnectionStatus
    data class Connected(val deviceName: String) : ConnectionStatus
    data class Error(val message: String) : ConnectionStatus
}

@SuppressLint("MissingPermission")
class BluetoothManager(private val context: Context) {

    private val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    
    // SPP UUID for Bluetooth Classic Serial Port Profile
    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    
    private var socket: BluetoothSocket? = null
    private var outputStream: OutputStream? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Auto-stop motor pulse duration in seconds (default 1.0s)
    var autoStopDurationSeconds: Float = 1.0f
    private var autoStopJob: Job? = null

    private val _connectionStatus = MutableStateFlow<ConnectionStatus>(ConnectionStatus.Disconnected)
    val connectionStatus: StateFlow<ConnectionStatus> = _connectionStatus.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices: StateFlow<List<BluetoothDevice>> = _pairedDevices.asStateFlow()

    val isBluetoothEnabled: Boolean
        get() = bluetoothAdapter?.isEnabled == true

    init {
        refreshPairedDevices()
    }

    fun refreshPairedDevices() {
        try {
            val bonded = bluetoothAdapter?.bondedDevices?.toList().orEmpty()
            // Sort so "PicoRobot" appears at the top if present
            val sorted = bonded.sortedByDescending { it.name == "PicoRobot" }
            _pairedDevices.value = sorted
        } catch (e: Exception) {
            Log.e("BluetoothManager", "Failed to get bonded devices", e)
            _pairedDevices.value = emptyList()
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (_connectionStatus.value is ConnectionStatus.Connecting) return

        scope.launch {
            _connectionStatus.value = ConnectionStatus.Connecting
            try {
                // Cancel discovery as it slows down connection
                bluetoothAdapter?.cancelDiscovery()

                val tmpSocket = device.createRfcommSocketToServiceRecord(sppUuid)
                tmpSocket.connect()

                socket = tmpSocket
                outputStream = tmpSocket.outputStream

                val name = device.name ?: "PicoRobot"
                _connectionStatus.value = ConnectionStatus.Connected(name)
                Log.d("BluetoothManager", "Successfully connected to $name")
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Connection failed", e)
                closeConnectionQuietly()
                _connectionStatus.value = ConnectionStatus.Error(e.localizedMessage ?: "Connection failed")
            }
        }
    }

    fun sendCommand(char: Char) {
        sendMovementCommand(char, autoStopDurationSeconds)
    }

    fun sendMovementCommand(char: Char, customDurationSeconds: Float = autoStopDurationSeconds) {
        scope.launch {
            try {
                val stream = outputStream
                if (_connectionStatus.value is ConnectionStatus.Connected && stream != null) {
                    // Cancel any existing pending auto-stop job
                    autoStopJob?.cancel()
                    autoStopJob = null

                    stream.write(char.code)
                    stream.flush()
                    Log.d("BluetoothManager", "Sent command: $char (pulse duration: ${customDurationSeconds}s)")

                    // Schedule automatic stop 'S' after delay for movement commands
                    val isMovementCommand = char in listOf('F', 'B', 'L', 'R', 'G', 'I')
                    if (isMovementCommand && customDurationSeconds > 0f) {
                        autoStopJob = scope.launch {
                            delay((customDurationSeconds * 1000).toLong())
                            if (_connectionStatus.value is ConnectionStatus.Connected) {
                                try {
                                    outputStream?.write('S'.code)
                                    outputStream?.flush()
                                    Log.d("BluetoothManager", "Auto-stop 'S' sent after ${customDurationSeconds}s")
                                } catch (e: Exception) {
                                    Log.e("BluetoothManager", "Failed to send auto-stop 'S'", e)
                                }
                            }
                        }
                    }
                } else {
                    Log.w("BluetoothManager", "Cannot send command '$char': Not connected")
                }
            } catch (e: IOException) {
                Log.e("BluetoothManager", "Failed to send command $char", e)
                handleDisconnect()
            }
        }
    }

    fun disconnect() {
        scope.launch {
            autoStopJob?.cancel()
            autoStopJob = null
            // Safety first: send stop command 'S' before closing if connected
            try {
                outputStream?.write('S'.code)
                outputStream?.flush()
            } catch (_: Exception) {}
            
            closeConnectionQuietly()
            _connectionStatus.value = ConnectionStatus.Disconnected
        }
    }

    private fun handleDisconnect() {
        autoStopJob?.cancel()
        autoStopJob = null
        closeConnectionQuietly()
        _connectionStatus.value = ConnectionStatus.Disconnected
    }

    private fun closeConnectionQuietly() {
        autoStopJob?.cancel()
        autoStopJob = null
        try {
            outputStream?.close()
        } catch (_: Exception) {}
        try {
            socket?.close()
        } catch (_: Exception) {}
        outputStream = null
        socket = null
    }

    fun cleanup() {
        disconnect()
        scope.cancel()
    }
}
