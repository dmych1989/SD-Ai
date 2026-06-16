// SDApp.kt —— Application 初始化
package com.sdaiapp

import android.app.Application
import android.content.Intent
import com.sdaiapp.utils.AppLog as Log
import com.sdaiapp.utils.CrashLogger
import java.io.File

class SDApp : Application() {

    companion object {
        const val TAG = "SDApp"
        lateinit var instance: SDApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // 初始化崩溃日志捕获
        CrashLogger.init(this)

        // 创建应用私有目录
        val modelDir = File(filesDir, "models")
        if (!modelDir.exists()) modelDir.mkdirs()

        val outputDir = File(filesDir, "outputs")
        if (!outputDir.exists()) outputDir.mkdirs()

        val logDir = File(filesDir, "logs")
        if (!logDir.exists()) logDir.mkdirs()

        // 启动日志自动回传服务
        try {
            val intent = Intent(this, com.sdaiapp.service.LogcatService::class.java)
            intent.action = com.sdaiapp.service.LogcatService.ACTION_START
            startForegroundService(intent)
            Log.i(TAG, "LogcatService 已启动")
        } catch (e: Exception) {
            Log.e(TAG, "LogcatService 启动失败: ${e.message}")
        }

        Log.i(TAG, "SDApp 初始化完成")
        Log.i(TAG, "模型目录: ${modelDir.absolutePath}")
        Log.i(TAG, "输出目录: ${outputDir.absolutePath}")
        Log.i(TAG, "日志目录: ${logDir.absolutePath}")
    }
}
