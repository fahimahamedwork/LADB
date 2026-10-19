package com.ladb.reborn.core

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.util.LruCache
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

data class AppEntry(
    val packageName: String,
    val label: String,
    val isSystem: Boolean,
    val enabled: Boolean,
    val versionName: String?,
    val versionCode: Long,
    val apkPath: String,
    val installTime: Long,
    val targetSdk: Int,
    val uid: Int,
    val permissions: List<String>,
)

/**
 * Installed-package catalog (PackageManager) + full-power actions executed
 * through the ADB shell uid via [AdbManager.shellCmd] — no root required.
 */
class AppsRepository(
    private val app: Application,
    private val adb: AdbManager,
) {
    private val pm = app.packageManager
    private val iconCache = object : LruCache<String, Drawable>(256) {
        override fun create(key: String): Drawable? =
            runCatching { pm.getApplicationIcon(key) }.getOrNull()
    }
    private val loadMutex = Mutex()

    private val _apps = MutableStateFlow<List<AppEntry>>(emptyList())
    val apps: StateFlow<List<AppEntry>> = _apps

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading

    fun iconFor(packageName: String): Drawable? = iconCache.get(packageName)

    suspend fun refresh(force: Boolean = false) {
        if (!force && _apps.value.isNotEmpty()) return
        loadMutex.withLock {
            if (!force && _apps.value.isNotEmpty()) return
            _loading.value = true
            try {
                val loaded = withContext(Dispatchers.IO) { queryPackages() }
                _apps.value = loaded
            } finally {
                _loading.value = false
            }
        }
    }

    private fun queryPackages(): List<AppEntry> {
        val flags = PackageManager.GET_PERMISSIONS
        val packages: List<PackageInfo> = runCatching { pm.getInstalledPackages(flags) }.getOrDefault(emptyList())
        return packages.mapNotNull { info ->
            val appInfo = info.applicationInfo ?: return@mapNotNull null
            val label = runCatching { appInfo.loadLabel(pm).toString() }.getOrDefault(info.packageName)
            AppEntry(
                packageName = info.packageName,
                label = label,
                isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                enabled = appInfo.enabled,
                versionName = info.versionName,
                versionCode = info.longVersionCode,
                apkPath = appInfo.sourceDir ?: "",
                installTime = info.firstInstallTime,
                targetSdk = appInfo.targetSdkVersion,
                uid = appInfo.uid,
                permissions = info.requestedPermissions?.toList().orEmpty(),
            )
        }.sortedWith(compareBy({ !it.enabled }, { it.isSystem }, { it.label.lowercase() }))
    }

    suspend fun searchPackageNames(): List<String> {
        refresh()
        return _apps.value.map { it.packageName }
    }

    // ---- Actions (run as shell uid) -------------------------------------------------

    suspend fun launch(pkg: String): Result<String> =
        adb.shellCmd("monkey -p $pkg -c android.intent.category.LAUNCHER 1")

    suspend fun forceStop(pkg: String): Result<String> =
        adb.shellCmd("am force-stop $pkg")

    suspend fun uninstall(pkg: String): Result<String> =
        adb.shellCmd("pm uninstall --user 0 $pkg")

    suspend fun clearData(pkg: String): Result<String> =
        adb.shellCmd("pm clear $pkg")

    suspend fun disable(pkg: String): Result<String> =
        adb.shellCmd("pm disable-user --user 0 $pkg")

    suspend fun enable(pkg: String): Result<String> =
        adb.shellCmd("pm enable $pkg")

    suspend fun grant(pkg: String, permission: String): Result<String> =
        adb.shellCmd("pm grant $pkg $permission")

    suspend fun revoke(pkg: String, permission: String): Result<String> =
        adb.shellCmd("pm revoke $pkg $permission")

    /** Parse `dumpsys package <pkg>` runtime permission grant states. */
    suspend fun grantedPermissions(pkg: String): Map<String, Boolean> {
        val dump = adb.shellCmd("dumpsys package $pkg", timeoutMs = 25_000L).getOrDefault("")
        val regex = Regex("""^\s*([a-zA-Z][\w.]+): granted=(true|false)""", RegexOption.MULTILINE)
        return regex.findAll(dump).associate { it.groupValues[1] to (it.groupValues[2] == "true") }
    }

    /** Copy the base APK into cache/exports and return a shareable content Uri. */
    suspend fun extractApk(entry: AppEntry): Result<Uri> = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(app.cacheDir, "exports").apply { mkdirs() }
            val safeName = entry.packageName.replace(Regex("[^\\w.]"), "_")
            val outFile = File(dir, "${entry.label.ifBlank { safeName }}-$safeName.apk")
            File(entry.apkPath).copyTo(outFile, overwrite = true)
            FileProvider.getUriForFile(app, "${app.packageName}.files", outFile)
        }
    }

    fun shareApk(uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            putExtra(Intent.EXTRA_STREAM, uri)
            type = "application/vnd.android.package-archive"
        }
        app.startActivity(Intent.createChooser(intent, "Share APK").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun launchFromDevice(context: Context, pkg: String) {
        val intent = pm.getLaunchIntentForPackage(pkg) ?: return
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
