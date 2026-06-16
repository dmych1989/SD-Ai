package com.sdaiapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.theme.*
import com.sdaiapp.utils.AppLog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogViewerScreen(onBack: () -> Unit) {
    var entries by remember { mutableStateOf(AppLog.getEntries()) }
    var tagFilter by remember { mutableStateOf("") }
    var levelFilter by remember { mutableStateOf("") }
    var autoScroll by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        while (true) {
            entries = AppLog.getFiltered(
                tagFilter = tagFilter.ifBlank { null },
                levelFilter = levelFilter.ifBlank { null },
                maxCount = 300
            )
            if (autoScroll && entries.isNotEmpty()) {
                listState.animateScrollToItem(entries.size - 1)
            }
            kotlinx.coroutines.delay(2000)
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BackgroundDark)) {
        // ── 顶部栏 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "返回", tint = TextPrimary)
            }
            Text("运行日志", color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = {
                        val allText = entries.joinToString("\n") { "${it.level}/${it.tag} ${it.timeStr} ${it.msg}" }
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(ClipData.newPlainText("logs", allText))
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "复制全部", tint = TextSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = { autoScroll = !autoScroll }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        if (autoScroll) Icons.Filled.VerticalAlignBottom else Icons.Filled.Pause,
                        contentDescription = "自动滚动",
                        tint = if (autoScroll) PrimaryIndigo else TextTertiary,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = { AppLog.clear() }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Delete, contentDescription = "清空", tint = TextTertiary, modifier = Modifier.size(18.dp))
                }
            }
        }

        // ── 过滤 + 统计行 ──
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 级别过滤
            LevelChip("全部", levelFilter == "", { levelFilter = "" })
            LevelChip("E", levelFilter == "E", { levelFilter = "E" })
            LevelChip("W", levelFilter == "W", { levelFilter = "W" })
            LevelChip("I", levelFilter == "I", { levelFilter = "I" })
            LevelChip("D", levelFilter == "D", { levelFilter = "D" })

            Spacer(modifier = Modifier.weight(1f))

            Text("${entries.size}条", color = TextTertiary, fontSize = 10.sp)
        }

        // ── 标签过滤 ──
        OutlinedTextField(
            value = tagFilter,
            onValueChange = { tagFilter = it },
            placeholder = { Text("标签过滤（如 SDEngine）", color = TextTertiary, fontSize = 11.sp) },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp).height(36.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(6.dp),
            textStyle = LocalTextStyle.current.copy(fontSize = 11.sp),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(4.dp))

        // ── 日志列表 ──
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            items(entries, key = { it.hashCode() }) { entry ->
                LogEntryCard(entry, context)
            }
        }
    }
}

@Composable
private fun LevelChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isSelected) PrimaryIndigo.copy(alpha = 0.25f) else SurfaceDark)
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            color = if (isSelected) PrimaryIndigo else TextSecondary,
            fontSize = 10.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
private fun LogEntryCard(entry: AppLog.Entry, context: Context) {
    val levelColor = when (entry.level) {
        "E" -> ErrorRed
        "W" -> WarningYellow
        "I" -> PrimaryIndigo
        "D" -> TextSecondary
        else -> TextTertiary
    }

    val bg = when (entry.level) {
        "E" -> ErrorRed.copy(alpha = 0.06f)
        "W" -> WarningYellow.copy(alpha = 0.04f)
        else -> SurfaceDark
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(4.dp))
            .background(bg)
            .clickable {
                val text = "${entry.level}/${entry.tag} ${entry.timeStr} ${entry.msg}"
                (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("log", text))
            }
            .padding(4.dp)
    ) {
        // 左侧色条
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(36.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(levelColor)
        )
        Spacer(modifier = Modifier.width(6.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 第一行：时间 + 标签
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    entry.timeStr,
                    color = TextTertiary,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    entry.tag,
                    color = levelColor.copy(alpha = 0.8f),
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium
                )
                // 级别小标签
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(levelColor.copy(alpha = 0.2f))
                        .padding(horizontal = 3.dp, vertical = 1.dp)
                ) {
                    Text(entry.level, color = levelColor, fontSize = 7.sp, fontWeight = FontWeight.Bold)
                }
            }

            // 第二行：消息内容
            Text(
                entry.msg,
                color = TextPrimary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 4,
                lineHeight = 13.sp
            )
        }
    }
}