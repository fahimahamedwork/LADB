package com.ladb.reborn.core

import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ConnState {
    data object Idle : ConnState
    data object Connecting : ConnState
    data class NeedsSetup(val reason: String) : ConnState
    data class Pairing(val message: String) : ConnState
    data class Connected(val serial: String) : ConnState
}

/**
 * Connection orchestrator:
 *  start-server -> (pair once) -> connect localhost:<mDNS port> -> persistent pty shell
 */
class AdbManager(
    private val app: Application,
    private val settings: SettingsRepository,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val mdns = MdnsDiscovery(app)
    val controller = TerminalController(app) { settings.settings.value.startupCommand }

    private val _state = MutableStateFlow<ConnState>(ConnState.Idle)
    val state: StateFlow<ConnState> = _state

    /** Short operational log lines shown in the pairing wizard. */
    private val _events = MutableStateFlow<List<String>>(emptyList())
    val events: StateFlow<List<String>> = _events

    private var bootstrapJob: Job? = null

    val hasWriteSecureSettings: Boolean
        get() = app.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    init {
        controller.onSessionFinished = {
            // Shell died unexpectedly — attempt a clean auto reconnect.
            scope.launch {
                log("Shell exited, reconnecting...")
                delay(1_000)
                if (_state.value is ConnState.Connected || _state.value is ConnState.Connecting) {
                    _state.value = ConnState.Connecting
                    bootstrap()
                }
            }
        }
    }

    fun log(message: String) {
        val stamped = "• $message"
        _events.value = (_events.value + stamped).takeLast(40)
    }

    /** Entry point on app launch: try stored port, then mDNS, else ask for setup. */
    fun bootstrap() {
        if (bootstrapJob?.isActive == true) return
        if (_state.value is ConnState.Connected) return
        scope.launch {
            _state.value = ConnState.Connecting
            log("Starting ADB server...")
            Adb.exec("start-server", timeoutMs = 60_000L)
            mdns.start()

            settings.lastPort?.let { port ->
                log("Trying last known port $port...")
                if (tryConnect(port)) return@launch
            }

            log("Discovering wireless debugging port (mDNS)...")
            val port = mdns.awaitConnectPort(12_000)
            if (port != null && tryConnect(port)) return@launch

            _state.value = ConnState.NeedsSetup(
                if (port == null)
                    "Wireless debugging port was not discovered. Make sure Wireless debugging is enabled, then retry."
                else
                    "Could not connect on port $port. Try pairing again."
            )
        }.also { bootstrapJob = it }
    }

    private suspend fun tryConnect(port: Int): Boolean {
        log("Connecting to localhost:$port ...")
        val result = Adb.exec("connect", "localhost:$port", timeoutMs = 20_000L)
        val out = result.getOrDefault("")
        if (out.isNotBlank()) log(out)
        val lowered = out.lowercase()
        val ok = lowered.contains("connected to") && !lowered.contains("failed")
        if (ok) {
            settings.lastPort = port
            startShell("localhost:$port")
            return true
        }
        return false
    }

    private suspend fun startShell(serial: String) {
        log("Starting interactive shell...")
        withContext(Dispatchers.Main) {
            controller.start(serial, columns = 80, rows = 24)
        }
        log("Connected. Happy hacking!")
        _state.value = ConnState.Connected(serial)
    }

    fun pair(pairingPort: String, code: String) {
        scope.launch {
            _state.value = ConnState.Pairing("Pairing with localhost:$pairingPort ...")
            log("Pairing with localhost:$pairingPort ...")
            val result = Adb.exec("pair", "localhost:$pairingPort", code, timeoutMs = 25_000L)
            val out = result.getOrDefault("")
            if (out.isNotBlank()) log(out)
            val ok = out.lowercase().contains("success")
            if (ok) {
                settings.pairedOnce = true
                log("Paired successfully!")
                _state.value = ConnState.Connecting
                val connectPort = mdns.awaitConnectPort(15_000)
                    ?: pairingPort.toIntOrNull()
                if (connectPort != null && tryConnect(connectPort)) return@launch
                _state.value = ConnState.NeedsSetup(
                    "Paired, but the connect port was not discovered yet. Toggle Wireless debugging or tap Connect."
                )
            } else {
                _state.value = ConnState.NeedsSetup(
                    "Pairing failed. Double-check the pairing code and port, then try again."
                )
            }
        }
    }

    fun connectManual(port: Int) {
        scope.launch {
            _state.value = ConnState.Connecting
            if (!tryConnect(port)) {
                _state.value = ConnState.NeedsSetup("Could not connect on port $port.")
            }
        }
    }

    /** One-shot `adb -s <serial> shell <cmd>` for App Manager actions. */
    suspend fun shellCmd(command: String, timeoutMs: Long = 20_000L): Result<String> {
        val serial = (state.value as? ConnState.Connected)?.serial
            ?: settings.lastPort?.let { "localhost:$it" }
            ?: return Result.failure(IllegalStateException("Not connected"))
        return withContext(Dispatchers.IO) {
            Adb.exec("-s", serial, "shell", *command.splitKeepQuoted().toTypedArray(), timeoutMs = timeoutMs)
        }
    }

    /** Full reset: forget port, kill server, force the pairing wizard. */
    fun resetConnection() {
        scope.launch {
            controller.destroy()
            Adb.exec("kill-server")
            settings.lastPort = null
            settings.pairedOnce = false
            _events.value = emptyList()
            log("ADB server killed. Start fresh.")
            _state.value = ConnState.NeedsSetup("Connection reset. Pair again to continue.")
        }
    }
}

/** Minimal tokenizer that keeps quoted args intact for `adb shell` argv passing. */
internal fun String.splitKeepQuoted(): List<String> {
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    var inQuote: Char? = null
    for (c in this) {
        when {
            inQuote != null && c == inQuote -> inQuote = null
            inQuote == null && (c == '"' || c == '\'') -> inQuote = c
            inQuote == null && c.isWhitespace() -> {
                if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
            }
            else -> sb.append(c)
        }
    }
    if (sb.isNotEmpty()) tokens.add(sb.toString())
    return tokens
}
