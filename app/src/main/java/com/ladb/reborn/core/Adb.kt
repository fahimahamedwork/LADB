package com.ladb.reborn.core

import android.content.Context
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Thin wrapper around the bundled AOSP adb client (libadb.so).
 * The binary is extracted to nativeLibraryDir at install time
 * (useLegacyPackaging = true) which preserves the executable bit.
 */
object Adb {

    lateinit var appContext: Context
        private set

    fun init(context: Context) {
        appContext = context.applicationContext
    }

    val adbPath: String
        get() = "${appContext.applicationInfo.nativeLibraryDir}/libadb.so"

    /**
     * Run a one-shot adb command and capture its combined output.
     */
    fun exec(vararg args: String, timeoutMs: Long = 30_000L): Result<String> {
        return try {
            val command = listOf(adbPath) + args.toList()
            val process = ProcessBuilder(command).apply {
                redirectErrorStream(true)
                environment()["HOME"] = appContext.filesDir.absolutePath
                environment()["TMPDIR"] = appContext.cacheDir.absolutePath
                environment()["PATH"] = environment()["PATH"] + ":" + appContext.applicationInfo.nativeLibraryDir
            }.start()

            val output = StringBuilder()
            val reader = Thread {
                try {
                    process.inputStream.bufferedReader().forEachLine { line ->
                        synchronized(output) { output.appendLine(line) }
                    }
                } catch (_: Exception) {
                }
            }.apply { isDaemon = true; start() }

            val finished = process.waitFor(timeoutMs, TimeUnit.MILLISECONDS)
            if (!finished) {
                process.destroyForcibly()
                Result.failure(IOException("Timed out running: adb ${args.joinToString(" ")}"))
            } else {
                // Give the daemon reader a beat to drain the tail of the stream.
                reader.join(200)
                val text = synchronized(output) { output.toString() }.trim()
                if (process.exitValue() == 0 || text.isNotEmpty()) {
                    Result.success(text)
                } else {
                    Result.failure(IOException("adb exited with ${process.exitValue()}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
