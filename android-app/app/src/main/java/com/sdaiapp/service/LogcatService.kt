// service/LogcatService.kt
// 前台服务：持续抓取 logcat 日志（用于调试阶段）
package com.sdaiapp.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import com.sdaiapp.utils.AppLog as Log
import com.sdaiapp.BuildConfig
import com.sdaiapp.R
import com.sdaiapp.utils.CrashLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 前台服务：实时抓取 logcat 并写入文件
 *
 * 用途：在测试阶段持续记录日志，方便复现和定位 bug
 * 启动：ContextCompat.startForegroundService(context, Intent(context, LogcatService::class.java))
 * 停止：context.stopService(Intent(context, LogcatService::class.java))
 *
 * 注意：正式发布版本应关闭此服务（或设为可选）
 */
class LogcatService : Service() {

    companion object {
        const val CHANNEL_ID = "sdai_logcat_channel"
        const val NOTIFICATION_ID = 10001
        const val ACTION_START = "START"
        const val ACTION_STOP = "STOP"
        private const val TAG = "LogcatService"
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private var logJob: Job? = null
    private var isRunning = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.i(TAG, "LogcatService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action ?: ACTION_START) {
            ACTION_START -> startLogging()
            ACTION_STOP  -> stopLogging()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startLogging() {
        if (isRunning) return
        isRunning = true

        // 启动前台通知
        startForeground(NOTIFICATION_ID, buildNotification("SD-AI 日志抓取中…"))

        Log.i(TAG, "开始实时 logcat 抓取")
        CrashLogger.logInfo("LogcatService started")

        logJob = scope.launch {
            try {
                // 清除旧 buffer
                Runtime.getRuntime().exec("logcat -c").waitFor()

                // 启动 logcat 进程（过滤 SDApp 相关 TAG）
                val process = Runtime.getRuntime().exec("logcat -v time *:V")
                val reader = BufferedReader(InputStreamReader(process.inputStream))

                val logFile = java.io.File(filesDir, "logs/live_logcat.txt")
                val writer = java.io.FileWriter(logFile, true)

                while (isActive && isRunning) {
                    val line = reader.readLine() ?: break
                    // 只写入 SDApp 相关日志（可按需放宽）
                    if (line.contains("SDApp")
                        || line.contains("SDAI")
                        || line.contains("MnnInference")
                        || line.contains("MnnScan")
                        || line.contains("CrashLogger")
                        || line.contains("AndroidRuntime")
                        || line.contains("FATAL")
                    ) {
                        writer.write(line)
                        writer.write("\n")
                        writer.flush()
                    }
                }
                writer.close()
                reader.close()
                process.destroy()

            } catch (e: Exception) {
                Log.e(TAG, "Logcat 抓取异常：${e.message}")
                CrashLogger.logException(e, "LogcatService")
            }
        }
    }

    private fun stopLogging() {
        isRunning = false
        logJob?.cancel()
        logJob = null
        CrashLogger.logInfo("LogcatService stopped")
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "LogcatService stopped")
    }

    private fun buildNotification(content: String): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }
        return builder
            .setContentTitle("SD-AI 日志服务")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SD-AI 日志抓取",
                NotificationManager.IMPORTANCE_LOW   // 不发声、不弹窗
            ).apply {
                description = "实时抓取 logcat 日志"
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopLogging()
        super.onDestroy()
    }
}
