package com.ladb.reborn.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import com.termux.terminal.TerminalSession
import com.termux.terminal.TerminalSessionClient

/**
 * Owns the single interactive `adb shell -t` session running under a real
 * pty (Termux terminal-emulator engine). ANSI output from the remote shell
 * is parsed by TerminalEmulator and rendered by the Compose terminal canvas.
 */
class TerminalController(
    private val appContext: Context,
    private val startupCommand: () -> String,
) {
    companion object {
        const val TRANSCRIPT_ROWS = 5000
    }

    var session: TerminalSession? = null
        private set

    /** Called on the main thread whenever the emulator buffer changed. */
    var onTextChange: (() -> Unit)? = null
    var onSessionFinished: (() -> Unit)? = null
    var onTitleChange: ((String) -> Unit)? = null

    @Volatile
    private var destroying = false

    val isRunning: Boolean
        get() = session?.isRunning == true

    val hasWriteSecureSettings: Boolean
        get() = appContext.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    private val client = object : TerminalSessionClient {
        override fun onTextChanged(changedSession: TerminalSession) {
            onTextChange?.invoke()
        }

        override fun onTitleChanged(changedSession: TerminalSession) {
            onTitleChange?.invoke(changedSession.title.orEmpty())
        }

        override fun onSessionFinished(finishedSession: TerminalSession) {
            if (!destroying) onSessionFinished?.invoke()
        }

        override fun onCopyTextToClipboard(session: TerminalSession, text: String?) {}
        override fun onPasteTextFromClipboard(session: TerminalSession?) {}
        override fun onBell(session: TerminalSession?) {}

        override fun onColorsChanged(session: TerminalSession) {
            onTextChange?.invoke()
        }

        override fun onTerminalCursorStateChange(state: Boolean) {}
        override fun getTerminalCursorStyle(): Int = 0

        override fun logError(tag: String?, message: String?) { Log.e(tag ?: "Termux", message ?: "") }
        override fun logWarn(tag: String?, message: String?) { Log.w(tag ?: "Termux", message ?: "") }
        override fun logInfo(tag: String?, message: String?) { Log.i(tag ?: "Termux", message ?: "") }
        override fun logDebug(tag: String?, message: String?) { Log.d(tag ?: "Termux", message ?: "") }
        override fun logVerbose(tag: String?, message: String?) { Log.v(tag ?: "Termux", message ?: "") }
        override fun logStackTraceWithMessage(tag: String?, message: String?, e: Exception?) {
            Log.e(tag ?: "Termux", message, e)
        }
        override fun logStackTrace(tag: String?, e: Exception?) { Log.e(tag ?: "Termux", "", e) }
    }

    /**
     * Start a fresh shell session against [serial] (e.g. "localhost:12345").
     * `shell -t` forces a remote pty so interactive programs and colors work.
     */
    fun start(serial: String, columns: Int, rows: Int) {
        destroy()
        destroying = false
        val filesDir = appContext.filesDir.absolutePath
        val cacheDir = appContext.cacheDir.absolutePath
        val nativeDir = appContext.applicationInfo.nativeLibraryDir

        val args = arrayOf(Adb.adbPath, "-s", serial, "shell", "-t")
        val env = arrayOf(
            "HOME=$filesDir",
            "TMPDIR=$cacheDir",
            "PATH=/system/bin:/system/xbin:$nativeDir",
            "ANDROID_DATA=/data",
            "ANDROID_ROOT=/system",
        )

        val newSession = TerminalSession(Adb.adbPath, filesDir, args, env, TRANSCRIPT_ROWS, client)
        newSession.initializeEmulator(columns, rows)
        session = newSession

        // Grant WRITE_SECURE_SETTINGS from the shell so future launches can
        // toggle Wireless debugging automatically.
        if (!hasWriteSecureSettings) {
            writeLine("pm grant ${appContext.packageName} android.permission.WRITE_SECURE_SETTINGS >/dev/null 2>&1")
        }
        val startup = startupCommand()
        if (startup.isNotBlank()) writeLine(startup)
    }

    fun writeLine(command: String) {
        val payload = (command + "\n").toByteArray(Charsets.UTF_8)
        session?.write(payload, 0, payload.size)
    }

    fun writeBytes(data: ByteArray) {
        session?.write(data, 0, data.size)
    }

    fun resize(columns: Int, rows: Int) {
        val s = session ?: return
        if (columns > 0 && rows > 0) s.updateSize(columns, rows)
    }

    fun destroy() {
        destroying = true
        session?.finishIfRunning()
        session = null
    }
}
