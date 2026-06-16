# 手机 APP 日志实时监控方法（教 AI 用）

## 一句话总结

> **APP 代码加标签 → Capacitor/Android 自动输出到系统日志 → 电脑用 adb 命令抓取 → AI 分析判断**

---

## 方法一：电脑远程监控（适合开发调试）

这是我在开发中用的方法，不需要修改 APP 代码，适合 AI 自动操作。

### 原理

```
手机 APP (console.log / android.util.Log)
       ↓  Capacitor/Android 框架自动转发
手机系统日志 (logcat 缓冲区)
       ↓  USB 连接 + adb 命令
电脑端抓取
       ↓  过滤、清洗、分析
AI 判断进度/错误
```

### 关键命令

```powershell
# 1. 一次性读取日志（不阻塞）
adb logcat -d

# 2. 按标签过滤
adb logcat -d | Select-String "BonsaiLog"

# 3. 增大缓冲区（防止旧日志被覆盖）
adb logcat -G 16M

# 4. 清空旧日志
adb logcat -c
```

### 核心技巧

| 技巧 | 说明 |
|------|------|
| **自定义标签** | 不要搜 `Error`（系统日志太多），用自己定义的标签如 `BonsaiLog`、`MNN`、`StableDiffusion` |
| **轮询而非实时流** | AI 不适合实时流式输出，用 `adb logcat -d` 每 30~60 秒抓一次 |
| **清洗前缀** | logcat 每行开头有时间戳、进程ID等垃圾，用正则去掉 |
| **增大缓冲区** | `adb logcat -G 16M` 确保日志不被冲掉 |

### 如何在 JS/TS 代码中埋日志

用 Capacitor 框架开发时，`console.log` 会自动出现在 logcat 中：

```javascript
// 关键位置加标签
console.log("[BonsaiLog] 开始生成");

// 进度信息
console.log("[BonsaiLog] 第 5/20 步完成");

// 错误信息
console.error("[BonsaiLog] 模型加载失败: ", error);

// 关键数据
console.log("[BonsaiLog] 生成耗时: 12.3秒");
```

### 如何在原生 Android 代码（Kotlin）中埋日志

```kotlin
import android.util.Log

Log.i("BonsaiLog", "开始加载模型")
Log.e("BonsaiLog", "错误信息: $error")
Log.d("BonsaiLog", "第${i}/${total}步")
```

### AI 执行步骤模板（教 AI 用的）：

```markdown
1. 每 30 秒执行一次：adb logcat -d
2. 过滤包含 [BonsaiLog] 的行
3. 去掉时间戳前缀（保留冒号后的内容）
4. 分析内容：
   - 看到 "开始生成" → 等待
   - 看到 "第 X/20 步" → 计算进度
   - 看到 "生成完成" → 结果出来了
   - 看到 "Error"/"失败" → 出问题了
5. 根据分析结果决定下一步操作
```

---

## 方法二：APP 内嵌日志查看器（适合用户自己看）

我在这个项目里已经实现了第二种方法——在 APP 设置页面直接加了一个「查看运行日志」按钮。

### 实现原理

```
自定义 AppLog 类
  ├── 同时写 android.util.Log（系统可见）
  └── 同时写内存循环缓冲区（500条）
         ↓
  LogViewerScreen 页面（每2秒自动刷新）
         ↓
  设置 → 查看运行日志 → 实时滚动显示
         ↓
  支持：按标签过滤 / 按级别过滤 / 暂停滚动 / 清空
```

### 涉及的代码文件

| 文件 | 功能 |
|------|------|
| `AppLog.kt` | 自定义日志工具，同时写 logcat + 内存缓冲区 |
| `LogViewerScreen.kt` | 日志查看页面 UI |
| `Navigation.kt` | 全屏显示日志页 |
| `SettingsScreen.kt` | 入口按钮（"查看运行日志"、"清空日志"） |

### 使用方法（给用户）

进入 APP → 设置 → 日志区域 → 点击「查看运行日志」

- **标签过滤**：输入 `MNN` 只看推理日志，输入 `StableDiffusion` 只看生成过程
- **级别过滤**：点 `E` 只看错误，点 `W` 只看警告
- **自动滚动**：底部按钮切换是否自动滚到最新
- **清空**：清除所有日志

---

## 给 AI 的教学要点

如果你要让另一个 AI 学会这个方法，教它这 3 步：

### 第 1 步：先埋日志
在 APP 代码的关键位置加上带标签的日志输出：

```
console.log("[我的标签] 开始加载模型")
```

### 第 2 步：用 adb 抓取
```powershell
adb logcat -d | Select-String "我的标签"
```

### 第 3 步：分析判断
AI 读取日志内容后，根据关键词决定下一步：

| 日志内容 | AI 判断 |
|----------|---------|
| "开始加载" | 等待中 |
| "第 5/20 步" | 进度 25% |
| "生成完成" | 任务结束 |
| "失败" / "Error" | 出错了，需要处理 |

---

## 常见问题

**Q: 手机必须用 USB 连电脑吗？**
A: 可以无线连接：`adb connect 手机IP:5555`，前提是手机和电脑在同一个 WiFi 下。

**Q: 日志被淹没了怎么办？**
A: 用自定义标签过滤，不要搜通用词。或者 `adb logcat -G 16M` 增大缓冲区。

**Q: 每次都要输密码吗？**
A: 首次连接需要在手机上授权，之后不用。