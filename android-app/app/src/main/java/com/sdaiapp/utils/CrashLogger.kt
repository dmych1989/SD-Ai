// utils/CrashLogger.kt
// 全局错误日志抓取 + 文件写入 + 分享
package com.sdaiapp.utils

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.os.Build
import com.sdaiapp.utils.AppLog as Log
import com.sdaiapp.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 全局日志抓取工具
 *
 * 功能：
 * 1. 捕获 Java Crash（UncaughtExceptionHandler）
 * 2. 捕获 Native Crash（logcat -b crash）
 * 3. 手动记录业务日志（logInfo / logError / logException）
 * 4. 日志文件自动按日期滚动
 * 5. 支持一键分享日志文件（通过系统分享 Intent）
 */
object CrashLogger {

    private const val TAG = "CrashLogger"
    private const val LOG_FILE_PREFIX = "sdai_crash"
    private const val MAX_LOG_FILES = 10   // 最多保留 10 个日志文件

    private lateinit var appContext: Context
    private lateinit var logDir: File
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /** 初始化（在 Application.onCreate 中调用）*/
    fun init(context: Context) {
        appContext = context.applicationContext
        logDir = File(context.filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()

        // 安装全局异常捕获
        installExceptionHandler()

        // 清理旧日志
        cleanOldLogs()

        logInfo("========== CrashLogger 初始化 ==========")
        logDeviceInfo()
    }

    /** 安装全局异常处理器 */
    private fun installExceptionHandler() {
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            logException(throwable, "UncaughtException in ${thread.name}")
            // 写入 fatal 日志文件后立即让系统处理（崩溃退出）
            writeFatalLog(throwable)
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    /** 记录普通信息日志 */
    fun logInfo(message: String) {
        Log.i(TAG, message)
        writeLog("INFO", message)
    }

    /** 记录错误日志 */
    fun logError(message: String) {
        Log.e(TAG, message)
        writeLog("ERROR", message)
    }

    /** 记录异常（含堆栈）*/
    fun logException(e: Throwable, context: String = "") {
        val sw = StringWriter()
        sw.write("${if (context.isNotEmpty()) "[$context] " else ""}")
        e.printStackTrace(PrintWriter(sw))
        val stackTrace = sw.toString()
        Log.e(TAG, stackTrace)
        writeLog("EXCEPTION", stackTrace)
    }

    /** 写入日志到文件（异步）*/
    private fun writeLog(level: String, message: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val logFile = getCurrentLogFile()
                val timestamp = dateFormat.format(Date())
                val line = "[$timestamp] [$level] $message\n"
                FileWriter(logFile, true).use { it.write(line) }
            } catch (e: Exception) {
                Log.e(TAG, "写入日志失败：${e.message}")
            }
        }
    }

    /** 写入致命错误日志（同步，崩溃前调用）*/
    private fun writeFatalLog(throwable: Throwable) {
        try {
            val fatalFile = File(logDir, "fatal_${fileDateFormat.format(Date())}.log")
            val sw = StringWriter()
            sw.write("========== FATAL CRASH ==========\n")
            sw.write("Time: ${dateFormat.format(Date())}\n")
            sw.write("Device: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})\n")
            sw.write("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
            sw.write("App Version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})\n")
            sw.write("========== STACK TRACE ==========\n")
            throwable.printStackTrace(PrintWriter(sw))
            sw.write("\n========== LOGCAT (last 200 lines) ==========\n")
            sw.write(getRecentLogcat(200))
            FileWriter(fatalFile).use { it.write(sw.toString()) }
            Log.i(TAG, "致命日志已写入：${fatalFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "写入致命日志失败：${e.message}")
        }
    }

    /** 获取当前日志文件（按日期）*/
    private fun getCurrentLogFile(): File {
        val today = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
        return File(logDir, "${LOG_FILE_PREFIX}_${today}.log")
    }

    /** 抓取最近 logcat 输出 */
    private fun getRecentLogcat(maxLines: Int): String {
        return try {
            val process = Runtime.getRuntime().exec("logcat -d -t $maxLines")
            process.inputStream.bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            "（无法获取 logcat：${e.message}）"
        }
    }

    /** 获取完整 logcat dump（用于「抓取日志」按钮）*/
    fun dumpFullLogcat(): File? {
        return try {
            val dumpFile = File(logDir, "logcat_dump_${fileDateFormat.format(Date())}.txt")
            val process = Runtime.getRuntime().exec("logcat -d")
            process.inputStream.bufferedReader().use { reader ->
                FileWriter(dumpFile).use { writer ->
                    writer.write("========== LOGCAT DUMP ==========\n")
                    writer.write("Time: ${dateFormat.format(Date())}\n")
                    writer.write("========== BEGIN ==========\n")
                    reader.copyTo(writer)
                }
            }
            Log.i(TAG, "Logcat dump 已保存：${dumpFile.absolutePath}")
            dumpFile
        } catch (e: Exception) {
            Log.e(TAG, "Logcat dump 失败：${e.message}")
            CrashLogger.logException(e, "dumpFullLogcat")
            null
        }
    }

    /** 分享日志文件（调起系统分享）*/
    fun shareLog(file: File): Intent {
        val uri = androidx.core.content.FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "SD-AI 错误日志")
            putExtra(Intent.EXTRA_TEXT, "请查看附件中的日志文件，帮助开发者定位问题。")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 记录设备信息 */
    private fun logDeviceInfo() {
        val info = mutableListOf<String>()
        info.add("设备：${Build.MANUFACTURER} ${Build.MODEL}")
        info.add("代号：${Build.DEVICE}")
        info.add("Android：${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        info.add("CPU ABI：${Build.SUPPORTED_ABIS.joinToString()}")
        info.add("App 版本：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        info.add("构建时间：${BuildConfig.BUILD_TIME}")
        info.forEach { logInfo(it) }
    }

    /** 清理过期日志文件 */
    private fun cleanOldLogs() {
        try {
            val files = logDir.listFiles { f -> f.name.endsWith(".log") || f.name.endsWith(".txt") }
            if (files != null && files.size > MAX_LOG_FILES) {
                files.sortedBy { it.lastModified() }
                    .take(files.size - MAX_LOG_FILES)
                    .forEach {
                        it.delete()
                        Log.i(TAG, "删除旧日志：${it.name}")
                    }
            }
        } catch (e: Exception) {
            Log.e(TAG, "清理旧日志失败：${e.message}")
        }
    }

    /** 获取所有日志文件（用于「导出所有日志」）*/
    fun getAllLogFiles(): List<File> {
        val files = logDir.listFiles { f ->
            f.name.endsWith(".log") || f.name.endsWith(".txt")
        }
        return files?.sortedByDescending { it.lastModified() } ?: emptyList()
    }
}
