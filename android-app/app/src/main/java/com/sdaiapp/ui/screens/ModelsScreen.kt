package com.sdaiapp.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.data.model.ModelInfo
import com.sdaiapp.inference.LocalModelInfo
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.MainViewModel
import kotlinx.coroutines.delay

@Composable
fun ModelsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val remoteModels by viewModel.availableModels.collectAsState()
    val localModels = uiState.localMnnModels

    var copyToastText by remember { mutableStateOf("") }

    // zip 文件选择器
    val zipLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.importZipModel(it) }
    }

    // .safetensors 文件选择器（SD 1.5 模型导入）
    var pendingSafetensorsUri by remember { mutableStateOf<Uri?>(null) }
    var showClipSkipDialog by remember { mutableStateOf(false) }
    val safetensorsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            pendingSafetensorsUri = it
            showClipSkipDialog = true
        }
    }

    // 重命名弹窗 state
    var renameTarget by remember { mutableStateOf<String?>(null) }
    var renameInput by remember { mutableStateOf("") }

    // 自动清除 Toast
    LaunchedEffect(copyToastText) {
        if (copyToastText.isNotEmpty()) {
            delay(2000)
            copyToastText = ""
        }
    }

    Box(modifier = modifier.fillMaxSize().background(BackgroundDark)) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // -------- Header --------
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "模型管理",
                            color = TextPrimary,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "本地 ${localModels.size} 个 / 远程 ${remoteModels.size} 个",
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        IconButton(
                            onClick = { viewModel.rescanMnnModels() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(SurfaceDark)
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = "刷新", tint = TextSecondary)
                        }
                    }
                }
            }

            // ======== 本地 MNN 模型区域 ========
            item {
                SectionHeader(title = "本地 MNN 模型", icon = Icons.Filled.PhoneAndroid)
            }

            // 导入按钮（ZIP 和 .safetensors 上下排列，两行大标题+小字说明）
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // ① 导入 ZIP 模型
                    Button(
                        onClick = { zipLauncher.launch("application/zip") },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(22.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "导入 ZIP 模型",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "预转好的 .mnn 包，即装即用",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    // ② 导入 .safetensors
                    Button(
                        onClick = { safetensorsLauncher.launch("*/*") },
                        modifier = Modifier.fillMaxWidth().height(64.dp),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AccentPurple
                        )
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(22.dp), tint = Color.White)
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.Start,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "导入 .safetensors",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                "App 内自动转 MNN · 仅支持 SD 1.5",
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.85f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // 模型存放路径
            item {
                ModelsPathCard(
                    isCopied = copyToastText.contains("路径"),
                    onCopied = { text -> copyToastText = text }
                )
            }

            // 解压进度
            if (uiState.isModelLoading) {
                item {
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), color = PrimaryIndigo, strokeWidth = 2.dp)
                            Text(uiState.modelLoadPhase.ifEmpty { "处理中..." }, color = TextPrimary, fontSize = 13.sp)
                        }
                    }
                }
            }

            // 本地模型列表
            if (localModels.isEmpty()) {
                item {
                    EmptyLocalModelsState()
                }
            } else {
                items(localModels, key = { it.dirPath }) { model ->
                    LocalMnnModelCard(
                        model = model,
                        isSelected = model.name == uiState.selectedMnnModel,
                        isLoaded = uiState.isMnnLoaded && model.name == uiState.selectedMnnModel,
                        onSelect = { viewModel.selectMnnModel(model.name) },
                        onLoad = { viewModel.loadMnnModel() },
                        onUnload = { viewModel.unloadMnnModel() },
                        onDelete = { viewModel.deleteMnnModel(model.name) },
                        onRequestRename = { oldName ->
                            renameTarget = oldName
                            renameInput = oldName
                        }
                    )
                }
            }

            // ======== 远程 PC 模型区域 ========
            if (uiState.isConnected) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    SectionHeader(title = "远程 PC 模型", icon = Icons.Filled.Computer)
                }

                if (remoteModels.isEmpty()) {
                    item {
                        Text("PC 端暂无模型", color = TextTertiary, fontSize = 13.sp, modifier = Modifier.padding(8.dp))
                    }
                } else {
                    items(remoteModels, key = { it.name }) { model ->
                        RemoteModelCard(
                            model = model,
                            isSelected = model.name == uiState.selectedModel,
                            onActivate = { viewModel.selectModel(model.name) }
                        )
                    }
                }
            }

            // ======== 转换提示 ========
            item {
                ConversionWarningCard()
            }
        }

        // Toast overlay
        if (copyToastText.isNotEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                Surface(
                    modifier = Modifier.padding(top = 16.dp).statusBarsPadding(),
                    shape = RoundedCornerShape(8.dp),
                    color = PrimaryIndigo,
                    shadowElevation = 8.dp
                ) {
                    Text(
                        text = copyToastText,
                        color = Color.White,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                    )
                }
            }
        }

        // Clip Skip 选择对话框（在 Box overlay 层调 @Composable）
        if (showClipSkipDialog && pendingSafetensorsUri != null) {
            ClipSkipDialog(
                onDismiss = { showClipSkipDialog = false; pendingSafetensorsUri = null },
                onConfirm = { clipSkip ->
                    val uri = pendingSafetensorsUri
                    showClipSkipDialog = false
                    pendingSafetensorsUri = null
                    uri?.let { viewModel.importSafetensorsModel(it, clipSkip = clipSkip) }
                }
            )
        }

        // 重命名弹窗
        if (renameTarget != null) {
            AlertDialog(
                onDismissRequest = { renameTarget = null },
                title = { Text("重命名模型", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
                text = {
                    Column {
                        Text(
                            "原名：${renameTarget}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = renameInput,
                            onValueChange = { renameInput = it },
                            singleLine = true,
                            label = { Text("新名称") },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                focusedBorderColor = PrimaryIndigo,
                                cursorColor = PrimaryIndigo,
                                focusedLabelColor = PrimaryIndigo
                            )
                        )
                        Text(
                            "提示：模型目录会被重命名，未避免与现有模型冲突，建议使用独特名称。",
                            color = TextTertiary,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val oldName = renameTarget
                        val newName = renameInput.trim()
                        renameTarget = null
                        if (oldName != null && newName.isNotEmpty() && newName != oldName) {
                            viewModel.renameMnnModel(oldName, newName)
                        }
                    }) {
                        Text("确定", color = PrimaryIndigo, fontWeight = FontWeight.SemiBold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameTarget = null }) {
                        Text("取消", color = TextSecondary)
                    }
                },
                containerColor = SurfaceDark
            )
        }
    }
}

// ===================== 组件 =====================

@Composable
private fun SectionHeader(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(20.dp))
        Text(title, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun LocalMnnModelCard(
    model: LocalModelInfo,
    isSelected: Boolean,
    isLoaded: Boolean,
    onSelect: () -> Unit,
    onLoad: () -> Unit,
    onUnload: () -> Unit,
    onDelete: () -> Unit,
    onRequestRename: (currentName: String) -> Unit
) {
    val context = LocalContext.current
    // 状态色：已加载=绿；已选未加载=紫；未选=灰
    val (iconBg, iconTint, statusColor, statusText) = when {
        isLoaded -> Quad(SuccessGreen, Color.White, SuccessGreen, "● 已加载")
        isSelected -> Quad(PrimaryIndigo, Color.White, PrimaryIndigo, "● 已选中")
        else -> Quad(SurfaceLight, TextTertiary, TextTertiary, "未选中")
    }
    val subModelsOk = model.hasUnet && model.hasVae && model.hasTextEncoder

    // 名字/状态 双击重命名（防误触，比长按更稳）
    var menuExpanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLoaded) SuccessGreen.copy(alpha = 0.06f)
            else if (isSelected) PrimaryIndigo.copy(alpha = 0.08f)
            else SurfaceDark
        ),
        border = if (isLoaded) BorderStroke(1.dp, SuccessGreen.copy(alpha = 0.4f))
        else if (isSelected) BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.5f))
        else null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(14.dp)
        ) {
            // 第一行：图标 + 名称 + 状态 + 重命名按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 图标
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isLoaded) Icons.Filled.CheckCircle else Icons.Filled.Memory,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // 模型名 + 状态
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        model.name,
                        color = TextPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        statusText,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 重命名 / 更多操作按钮（右上角三点）
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.MoreVert,
                            contentDescription = "更多",
                            tint = TextSecondary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("重命名", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                onRequestRename(model.name)  // 触发外层弹窗
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("复制名称", fontSize = 14.sp) },
                            leadingIcon = {
                                Icon(Icons.Filled.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                menuExpanded = false
                                val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE)
                                    as android.content.ClipboardManager
                                cm.setPrimaryClip(android.content.ClipData.newPlainText("model", model.name))
                            }
                        )
                    }
                }
            }

            // 第二行：子模型完整性 + 大小
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ModelTag("UNet", model.hasUnet)
                ModelTag("VAE", model.hasVae)
                ModelTag("Text", model.hasTextEncoder)
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    formatBytes(model.sizeBytes),
                    color = TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // 第三行：操作按钮
            if (isSelected || isLoaded) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isLoaded) {
                        // 已加载 → 只显示「卸载」按钮
                        Button(
                            onClick = onUnload,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = WarningYellow.copy(alpha = 0.2f),
                                contentColor = WarningYellow
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("卸载", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else if (subModelsOk) {
                        // 已选 + 完整 → 显示「加载」按钮
                        Button(
                            onClick = onLoad,
                            modifier = Modifier.weight(1f).height(36.dp),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("加载到内存", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    } else {
                        // 已选 + 不完整 → 提示缺失
                        Surface(
                            color = ErrorRed.copy(alpha = 0.12f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(36.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("子模型缺失", color = ErrorRed, fontSize = 12.sp)
                            }
                        }
                    }
                    // 删除按钮
                    IconButton(
                        onClick = onDelete,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(ErrorRed.copy(alpha = 0.12f))
                    ) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除", tint = ErrorRed, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}

// 四元组辅助：状态色打包
private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

@Composable
private fun RemoteModelCard(
    model: ModelInfo,
    isSelected: Boolean,
    onActivate: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryIndigo.copy(alpha = 0.08f) else SurfaceDark
        ),
        border = if (isSelected) BorderStroke(1.dp, PrimaryIndigo.copy(alpha = 0.5f)) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onActivate)
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 图标
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (isSelected) PrimaryIndigo else SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Computer,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }

            // 模型名 + 状态
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    model.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    if (isSelected) "● 当前激活" else "点击激活",
                    color = if (isSelected) PrimaryIndigo else TextTertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun ModelTag(label: String, exists: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (exists) SuccessGreen.copy(alpha = 0.15f) else WarningYellow.copy(alpha = 0.15f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(label, color = if (exists) SuccessGreen else WarningYellow, fontSize = 9.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModelsPathCard(isCopied: Boolean, onCopied: (String) -> Unit) {
    val context = LocalContext.current
    val modelDirPath = remember {
        "/sdcard/Android/data/com.sdaiapp/files/models"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Folder, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(18.dp))
                Text("模型存放路径", color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(BackgroundDark)
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(modelDirPath, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                IconButton(
                    onClick = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("path", modelDirPath))
                        onCopied("路径已复制")
                    },
                    modifier = Modifier.size(32.dp).clip(RoundedCornerShape(6.dp)).background(if (isCopied) SuccessGreen.copy(alpha = 0.2f) else PrimaryIndigo)
                ) {
                    Icon(if (isCopied) Icons.Filled.Check else Icons.Filled.ContentCopy, contentDescription = "复制", tint = if (isCopied) SuccessGreen else Color.White, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyLocalModelsState() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Filled.Memory, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(40.dp))
            Text("暂无本地模型", color = TextSecondary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text("点击上方「导入 ZIP 模型」按钮，选择 .zip 格式的 MNN 模型压缩包，会自动解压到模型目录", color = TextTertiary, fontSize = 12.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun ConversionWarningCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PrimaryIndigo.copy(alpha = 0.08f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(Icons.Filled.TipsAndUpdates, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
            Text(
                "现在支持两种导入方式：\n• 导入 ZIP 模型：预转好的 .mnn 包（即装即用）\n• 导入 .safetensors：app 内自动转 MNN（仅支持 SD 1.5 架构）\n\nGGUF / SDXL / Flux 需要先用 PC 工具转好再导入。",
                color = TextSecondary,
                fontSize = 12.sp,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
private fun ClipSkipDialog(
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var selected by remember { mutableStateOf(2) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CLIP Skip 设置", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    "CLIP Skip 决定 CLIP 文本编码器在哪一层停下。选错了不影响运行，但会影响出图质量。",
                    fontSize = 13.sp,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(12.dp))
                listOf(
                    1 to "Skip 1（写实照片风 / 通用 SD）",
                    2 to "Skip 2（动漫 / 二次元 / 大多数 checkpoint）"
                ).forEach { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selected = value }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selected == value,
                            onClick = { selected = value },
                            colors = RadioButtonDefaults.colors(
                                selectedColor = PrimaryIndigo,
                                unselectedColor = TextTertiary
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(label, fontSize = 14.sp, color = TextPrimary)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected) }) {
                Text("开始转换", color = PrimaryIndigo, fontWeight = FontWeight.Medium)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary)
            }
        },
        containerColor = SurfaceDark,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}
