// 本地硬件监控工具 —— 读取手机 CPU / 内存 / GPU / 温度 实时使用率
//
// 设计原则：
// 1. 优先读不需 root 权限的路径（/proc/*）
// 2. SELinux 阻挡的路径（/sys/class/devfreq /sys/class/thermal）只试一次且全部 catch
// 3. 任何路径读不到都返回 -1（不抛异常，不污染 logcat）
// 4. 支持多 SoC：高通 kgsl、联发科 mali、紫光展锐等
// 5. SELinux 全面阻挡时的降级（MIUI 14 / Android 14 常见）：
//    - 温度：BatteryManager.BATTERY_PROPERTY_TEMPERATURE
//    - GPU 利用率：解析 dumpsys cpuinfo 中 mali-* 进程 CPU 占用率之和
package com.sdaiapp.utils

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import java.io.File

object LocalHardwareMonitor {

    data class Snapshot(
        val cpuUsage: Float,        // 0~100，-1=读不到
        val ramUsedGb: Float,       // GB
        val ramTotalGb: Float,      // GB
        val gpuUsage: Float,        // 0~100，-1=读不到
        val gpuFreqMhz: Int,        // MHz，-1=读不到
        val cpuTemp: Float          // ℃，-1=读不到
    )

    // /proc/stat 上次采样（用于算差值）—— 加 @Volatile 保证 IO 协程可见性
    @Volatile private var lastStatTotal: Long = -1
    @Volatile private var lastStatIdle: Long = -1
    @Volatile private var lastStatTime: Long = 0
    @Volatile private var cachedCpuUsage: Float = -1f

    // 调试计数器（连续失败 N 次后跳过 /proc/stat 加快轮询）
    @Volatile private var cpuStatReadFailCount: Int = 0

    /**
     * CPU 使用率 —— 优先用 /proc/self/stat（自己进程，Android 14 MIUI 也允许）
     * MIUI 14 / Android 14 上 /proc/stat 被 SELinux 锁（Permission denied），
     * /proc/self/stat 是相对路径且只读自己，应用内一定能读。
     *
     * 返回值反映"本 App 进程 CPU 占用率"，推理时能反映 SD 工作负载
     */
    fun readCpuUsage(): Float {
        return readCpuUsageFromSelf()
    }

    /**
     * Fallback：基于本进程 CPU 时间的近似值
     * Android 14 MIUI 14 上 /proc/stat 被 SELinux 锁，只能读 /proc/self/stat（自己）
     *
     * 返回值归一化到 0~100%：
     *  - 单核 100% = 一个核全速
     *  - 4 核全速 = 400%（这是 Linux 语义）
     *  归一化用本进程线程数（/proc/self/status Threads:），这样 4 线程跑满 ≈ 100%
     */
    private var lastProcessCpuNs: Long = -1
    private var lastProcessElapsedMs: Long = -1
    private fun readCpuUsageFromSelf(): Float {
        try {
            val stat = File("/proc/self/stat").readText().trim()
            // 第 14 字段 = utime（用户态 ticks），第 15 字段 = stime（系统态 ticks）
            val parts = stat.split(" ")
            if (parts.size > 15) {
                val utime = parts[13].toLongOrNull() ?: 0L
                val stime = parts[14].toLongOrNull() ?: 0L
                val nowNs = SystemClock.elapsedRealtimeNanos()
                val totalTicks = utime + stime
                if (lastProcessCpuNs >= 0 && lastProcessElapsedMs > 0) {
                    val elapsedNs = nowNs - lastProcessElapsedMs
                    if (elapsedNs > 0) {
                        // 100 Hz tick（CONFIG_HZ=100）→ 1 tick = 10ms = 10_000_000 ns
                        val tickNs = (totalTicks - lastProcessCpuNs) * 10_000_000L
                        val cores = getThreadCount().coerceAtLeast(1)
                        // 归一化：除以线程数（粗略认为每个线程跑在一个核上）
                        val pct = (tickNs.toFloat() / elapsedNs / cores * 100f).coerceIn(0f, 100f)
                        lastProcessCpuNs = totalTicks
                        lastProcessElapsedMs = nowNs
                        return pct
                    }
                }
                lastProcessCpuNs = totalTicks
                lastProcessElapsedMs = nowNs
            }
        } catch (_: Exception) {}
        return -1f
    }

    // 缓存本进程线程数（值不会频繁变化）
    @Volatile private var cachedThreads: Int = -1
    private fun getThreadCount(): Int {
        if (cachedThreads > 0) return cachedThreads
        return try {
            val status = File("/proc/self/status").readText()
            val match = Regex("Threads:\\s*(\\d+)").find(status)
            val n = match?.groupValues?.get(1)?.toIntOrNull() ?: 1
            cachedThreads = n
            n
        } catch (_: Exception) { 1 }
    }

    fun readMemory(context: Context): FloatArray {
        try {
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val info = ActivityManager.MemoryInfo()
            am.getMemoryInfo(info)
            val totalGb = info.totalMem / (1024f * 1024f * 1024f)
            val usedGb = (info.totalMem - info.availMem) / (1024f * 1024f * 1024f)
            return floatArrayOf(usedGb, totalGb)
        } catch (_: Exception) {
            return floatArrayOf(0f, 0f)
        }
    }

    // GPU 使用率 —— 兼容多 SoC
    // 优先级：
    //   1. 高通 Adreno: /sys/class/kgsl/kgsl-3d0/gpubusy
    //   2. 联发科 Mali: /proc/mali/utilization（旧版）/ /proc/gpufreq/gpufreq_utm_read
    //   3. devfreq: /sys/class/devfreq/<dir>/cur_load（SELinux 可能挡）
    //   4. 终极降级：dumpsys cpuinfo 中 mali-* 进程 CPU 占用率之和（MIUI 14 SELinux 锁了 sysfs 也能用）
    fun readGpuUsage(): Float {
        // 1. 高通 Adreno
        tryReadGpuUsage { File("/sys/class/kgsl/kgsl-3d0/gpubusy").readText() }?.let { return it }
        // 2. 联发科 Mali
        tryReadGpuUsage { File("/proc/mali/utilization").readText() }?.let { return it }
        tryReadGpuUsage { File("/proc/gpufreq/gpufreq_utm_read").readText() }?.let { return it }
        // 3. devfreq 通用（SELinux 可能挡，但不抛 SecurityException 时用）
        try {
            val gpuDirs = File("/sys/class/devfreq").listFiles()?.filter {
                it.name.contains("gpu", ignoreCase = true) || it.name.contains("mali", ignoreCase = true)
            } ?: emptyList()
            for (dir in gpuDirs) {
                for (loadName in listOf("cur_load", "load", "gpu_load", "utilization", "utilisation")) {
                    val loadFile = File(dir, loadName)
                    if (loadFile.exists() && loadFile.canRead()) {
                        val load = loadFile.readText().trim().toFloatOrNull()
                        if (load != null && load >= 0f) return load.coerceIn(0f, 100f)
                    }
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        // 4. 终极降级：dumpsys cpuinfo 解析 mali-* 进程 CPU 占用率
        // dumpsys 是不需 root 的，输出格式：
        //   "  12.3% 1234/mali-event-hand: 5% user + 7% kernel"
        // 把所有 mali 进程 CPU% 累加，最高达 100
        try {
            val proc = Runtime.getRuntime().exec(arrayOf("dumpsys", "cpuinfo"))
            val out = proc.inputStream.bufferedReader().readText()
            proc.waitFor()
            val regex = Regex("""^\s*(\d+\.?\d*)%.*mali[-:][^\s:]+""", RegexOption.MULTILINE)
            val sum = regex.findAll(out).sumOf { it.groupValues[1].toDoubleOrNull() ?: 0.0 }
            if (sum > 0) return sum.toFloat().coerceIn(0f, 100f)
        } catch (_: Exception) {
        }
        return -1f
    }

    // 尝试读 GPU 使用率（多 SoC 路径）
    private inline fun tryReadGpuUsage(read: () -> String): Float? {
        return try {
            val text = read().trim()
            if (text.isEmpty()) return null
            // kgsl 格式 "busy total"
            val parts = text.split("\\s+".toRegex())
            if (parts.size >= 2) {
                val busy = parts[0].toLongOrNull()
                val total = parts[1].toLongOrNull()
                if (busy != null && total != null && total > 0) {
                    return (busy.toFloat() / total * 100f).coerceIn(0f, 100f)
                }
            }
            // mali 格式: 数字或 "X%"
            val num = text.filter { it.isDigit() || it == '.' }.toFloatOrNull()
            if (num != null && num >= 0f) num.coerceIn(0f, 100f) else null
        } catch (_: SecurityException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    fun readGpuFreqMhz(): Int {
        // 高通
        try {
            val text = File("/sys/class/kgsl/kgsl-3d0/gpuclk").readText().trim()
            val hz = text.toLongOrNull() ?: 0L
            if (hz > 0) return (hz / 1_000_000).toInt()
        } catch (_: Exception) { }
        // devfreq
        try {
            val gpuDirs = File("/sys/class/devfreq").listFiles()?.filter {
                it.name.contains("gpu", ignoreCase = true) || it.name.contains("mali", ignoreCase = true)
            } ?: return -1
            for (dir in gpuDirs) {
                val freqFile = File(dir, "cur_freq")
                if (freqFile.exists() && freqFile.canRead()) {
                    val hz = freqFile.readText().trim().toLongOrNull() ?: continue
                    if (hz > 0) return (hz / 1_000_000).toInt()
                }
            }
        } catch (_: Exception) { }
        return -1
    }

    // CPU 温度 —— 读 /sys/class/thermal
    // SELinux 可能挡（logcat 会出现 avc 警告），所有失败路径都 catch
    // MIUI 14 / Android 14 上 /sys/class/thermal 几乎全被 SELinux 锁 → 降级到 BatteryManager
    fun readCpuTemp(context: Context? = null): Float {
        // 1. sysfs 优先（type=CPU/SOC/cluster/tsens 的 zone）
        try {
            val zones = File("/sys/class/thermal").listFiles()?.filter {
                it.name.startsWith("thermal_zone")
            } ?: emptyList()
            for (zone in zones) {
                try {
                    val typeFile = File(zone, "type")
                    if (!typeFile.exists() || !typeFile.canRead()) continue
                    val type = typeFile.readText().trim().lowercase()
                    if (type.contains("cpu") || type.contains("soc") || type.contains("cluster") || type.contains("tsens")) {
                        val tempFile = File(zone, "temp")
                        if (!tempFile.exists() || !tempFile.canRead()) continue
                        val raw = tempFile.readText().trim().toFloatOrNull() ?: continue
                        return if (raw > 1000f) raw / 1000f else raw
                    }
                } catch (_: SecurityException) {
                    continue
                } catch (_: Exception) {
                    continue
                }
            }
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
        // 2. 终极降级：电池温度（电池温度一般接近 CPU 温度，±5℃）
        // 用 sticky broadcast 拿（任何 Android 版本都能用）
        if (context != null) {
            try {
                val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
                val intent = context.registerReceiver(null, filter)
                val temp = intent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
                if (temp > 0) return temp / 10f
            } catch (_: Exception) { }
        }
        return -1f
    }

    fun snapshot(context: Context): Snapshot {
        return Snapshot(
            cpuUsage = readCpuUsage(),
            ramUsedGb = readMemory(context)[0],
            ramTotalGb = readMemory(context)[1],
            gpuUsage = readGpuUsage(),
            gpuFreqMhz = readGpuFreqMhz(),
            cpuTemp = readCpuTemp(context)
        )
    }
}
