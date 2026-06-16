package com.sdaiapp.utils

import android.util.Log as AndroidLog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 自定义日志 — 同时写系统 logcat + 内存循环缓冲区
 *
 * 用法：把 import android.util.Log 换成 import com.sdaiapp.utils.AppLog as Log
 * 然后继续用 Log.i / Log.d / Log.e 不需要改任何其他代码
 */
object AppLog {

    data class Entry(
        val level: String,  // I/D/E/W
        val tag: String,
        val msg: String,
        val time: Long = System.currentTimeMillis()
    ) {
        val timeStr: String get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(time))
        val display: String get() = "$timeStr [$level/$tag] $msg"
    }

    // 循环缓冲区：最多保留 500 条最新日志
    private val buffer = ArrayDeque<Entry>(500)

    @Synchronized
    fun getEntries(): List<Entry> = buffer.toList()

    @Synchronized
    fun clear() = buffer.clear()

    @Synchronized
    fun getFiltered(tagFilter: String? = null, levelFilter: String? = null, maxCount: Int = 200): List<Entry> {
        return buffer.asSequence()
            .filter { tagFilter == null || it.tag.contains(tagFilter, ignoreCase = true) }
            .filter { levelFilter == null || it.level == levelFilter }
            .toList()
            .takeLast(maxCount)
    }

    private fun add(level: String, tag: String, msg: String, tr: Throwable? = null) {
        val fullMsg = if (tr != null) "$msg\n${android.util.Log.getStackTraceString(tr)}" else msg
        synchronized(this) {
            buffer.addLast(Entry(level, tag, fullMsg))
            while (buffer.size > 500) buffer.removeFirst()
        }
    }

    // ─── 同 Android Log 一样的方法签名 ────────────────

    fun v(tag: String, msg: String): Int {
        AndroidLog.v(tag, msg); add("V", tag, msg); return 0
    }

    fun d(tag: String, msg: String): Int {
        AndroidLog.d(tag, msg); add("D", tag, msg); return 0
    }

    fun i(tag: String, msg: String): Int {
        AndroidLog.i(tag, msg); add("I", tag, msg); return 0
    }

    fun w(tag: String, msg: String): Int {
        AndroidLog.w(tag, msg); add("W", tag, msg); return 0
    }

    fun e(tag: String, msg: String): Int {
        AndroidLog.e(tag, msg); add("E", tag, msg); return 0
    }

    fun e(tag: String, msg: String, tr: Throwable): Int {
        AndroidLog.e(tag, msg, tr); add("E", tag, msg, tr); return 0
    }

    // 简写
    fun i(tag: String, msg: () -> String) = i(tag, msg())
    fun d(tag: String, msg: () -> String) = d(tag, msg())
    fun e(tag: String, msg: () -> String) = e(tag, msg())
}