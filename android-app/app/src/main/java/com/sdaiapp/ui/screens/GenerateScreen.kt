package com.sdaiapp.ui.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.i18n.AppText
import com.sdaiapp.ui.i18n.rememberText
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.AppUiState
import com.sdaiapp.ui.viewmodel.GenerationMode
import com.sdaiapp.ui.viewmodel.MainViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GenerateScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val t = rememberText(uiState)
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // 错误提示
    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        HeaderSection(isConnected = uiState.isConnected, useLocalInference = uiState.useLocalInference, error = uiState.connectionError, t = t)

        Spacer(modifier = Modifier.height(12.dp))

        // 推理模式选择：远程PC / 本地MNN
        InferenceModeBar(
            useLocal = uiState.useLocalInference,
            onToggle = { viewModel.toggleInferenceMode() },
            t = t
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 【删除】统一状态栏 UnifiedStatusBar（CPU/内存/GPU/温度）
        // — 用户要求"去掉生成页面上方本地mnn下方的cpu/内存/gpu模块"
        // — 硬件监控信息统一在侧边栏底部显示

        // 本地 MNN 模型选择（仅本地模式显示）
        if (uiState.useLocalInference) {
            MnnModelSection(
                models = uiState.localMnnModels,
                selected = uiState.selectedMnnModel,
                isLoaded = uiState.isMnnLoaded,
                onSelect = viewModel::selectMnnModel,
                onLoad = viewModel::loadMnnModel,
                onRescan = viewModel::rescanMnnModels,
                t = t
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 模式切换：文生图 / 图生图
        ModeSwitcher(
            mode = uiState.mode,
            onModeChange = { newMode ->
                viewModel.updateMode(newMode)
            },
            t = t
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 基础图（图生图用）
        if (uiState.mode == GenerationMode.IMG2IMG) {
            BaseImageSection(
                bitmap = uiState.baseImageBitmap,
                b64 = uiState.baseImageB64,
                onPickImage = { /* 见下方 launcher */ },
                onRemove = { viewModel.clearBaseImage() },
                onImagePicked = { b64, bitmap ->
                    viewModel.setBaseImage(b64, bitmap)
                },
                t = t
            )
            Spacer(modifier = Modifier.height(16.dp))
            DenoisingSlider(
                value = uiState.denoisingStrength,
                onValueChange = viewModel::updateDenoisingStrength,
                t = t
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        // 提示词
        PromptSection(
            prompt = uiState.prompt,
            negativePrompt = uiState.negativePrompt,
            onPromptChange = viewModel::updatePrompt,
            onNegativePromptChange = viewModel::updateNegativePrompt,
            t = t
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 当前参数（只读标签）—— 实际调整在「参数」页
        CurrentParamsLabel(
            width = uiState.width,
            height = uiState.height,
            steps = uiState.steps,
            cfgScale = uiState.cfgScale,
            seed = uiState.seed,
            sampler = uiState.sampler,
            t = t
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 模型选择（仅远程模式显示，本地模式已经选了）
        if (!uiState.useLocalInference) {
            ModelSection(
                activeModel = uiState.activeModel,
                models = uiState.availableModels,
                onSelectModel = viewModel::selectModel,
                t = t
            )
        }

        // 模型加载进度条（模型选择下方）
        if (uiState.isModelLoading) {
            Spacer(modifier = Modifier.height(10.dp))
            Card(
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceDark),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = PrimaryIndigo, strokeWidth = 2.dp)
                        Text(uiState.modelLoadPhase.ifEmpty { t.modelLoading }, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { uiState.modelLoadProgress },
                        modifier = Modifier.fillMaxWidth(),
                        color = PrimaryIndigo,
                        trackColor = BorderDark
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 生成按钮 + 进度

        val isLocal = uiState.useLocalInference
        val canGen = if (isLocal) {
            uiState.prompt.isNotBlank() && uiState.isMnnLoaded
        } else {
            uiState.prompt.isNotBlank() && uiState.activeModel.isNotEmpty() && uiState.isConnected
        }

        GenerateButton(
            isGenerating = uiState.isGenerating,
            isCancelling = uiState.isCancelling,
            canGenerate = canGen,
            onGenerate = { if (isLocal) viewModel.generateWithMNN() else viewModel.generateImage() },
            onCancel = { if (isLocal) viewModel.cancelMnnGeneration() else viewModel.cancelGeneration() },
            t = t
        )

        // 进度详情
        if (uiState.isGenerating) {
            Spacer(modifier = Modifier.height(12.dp))
            ProgressDetailSection(uiState, t = t)
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 结果
        if (uiState.outputImage != null || uiState.error != null) {
            ResultSection(
                state = uiState,
                context = context,
                onCopy = { bitmap ->
                    copyBitmapToClipboard(context, bitmap, t)
                },
                onSave = { bitmap ->
                    scope.launch {
                        val saved = saveBitmapToGallery(context, bitmap)
                        val msg = if (saved) t.savedToAlbum else t.saveFailed
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                },
                onShare = { bitmap ->
                    shareBitmap(context, bitmap, t)
                },
                t = t
            )
        }
    }
}

@Composable
private fun HeaderSection(isConnected: Boolean, useLocalInference: Boolean, error: String?, t: AppText) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(
                text = t.generateTitle,
                color = TextPrimary,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = if (useLocalInference) "本地推理模式" else if (isConnected) t.connected else t.disconnected,
                color = if (useLocalInference || isConnected) TextSecondary else TextTertiary,
                fontSize = 12.sp
            )
        }
        ConnectionStatusBadge(isConnected = isConnected, useLocalInference = useLocalInference, t = t)
    }
}

@Composable
private fun ConnectionStatusBadge(isConnected: Boolean, useLocalInference: Boolean, t: AppText) {
    val bg: Color
    val fg: Color
    val text: String
    if (useLocalInference) {
        bg = SuccessGreen.copy(alpha = 0.15f)
        fg = SuccessGreen
        text = "本地"
    } else if (isConnected) {
        bg = SuccessGreen.copy(alpha = 0.15f)
        fg = SuccessGreen
        text = "PC"
    } else {
        bg = ErrorRed.copy(alpha = 0.15f)
        fg = ErrorRed
        text = t.offline
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(fg)
        )
        Text(text, color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ModeSwitcher(mode: GenerationMode, onModeChange: (GenerationMode) -> Unit, t: AppText) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(4.dp)
    ) {
        ModeButton(
            label = "文生图",
            icon = Icons.Filled.TextFields,
            isSelected = mode == GenerationMode.TXT2IMG,
            onClick = { onModeChange(GenerationMode.TXT2IMG) },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = "图生图",
            icon = Icons.Filled.Image,
            isSelected = mode == GenerationMode.IMG2IMG,
            onClick = { onModeChange(GenerationMode.IMG2IMG) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ModeButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (isSelected) PrimaryIndigo.copy(alpha = 0.2f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = if (isSelected) PrimaryIndigo else TextSecondary, modifier = Modifier.size(18.dp))
            Text(label, color = if (isSelected) PrimaryIndigo else TextSecondary, fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal)
        }
    }
}

@Composable
private fun BaseImageSection(
    bitmap: Bitmap?,
    b64: String?,
    onPickImage: () -> Unit,
    onRemove: () -> Unit,
    onImagePicked: (String, Bitmap) -> Unit,
    t: AppText
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri ?: return@rememberLauncherForActivityResult
        try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val pickedBitmap = android.graphics.BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (pickedBitmap != null) {
                val baos = java.io.ByteArrayOutputStream()
                pickedBitmap.compress(Bitmap.CompressFormat.PNG, 90, baos)
                val bytes = baos.toByteArray()
                val encoded = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
                onImagePicked(encoded, pickedBitmap)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "读取图片失败：${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    Column {
        Text(t.baseImage, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        if (bitmap != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = "基础图",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxSize()
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SmallIconButton(icon = Icons.Filled.Refresh, onClick = { launcher.launch("image/*") })
                    SmallIconButton(icon = Icons.Filled.Close, onClick = onRemove)
                }
            }
        } else {
            OutlinedButton(
                onClick = { launcher.launch("image/*") },
                modifier = Modifier.fillMaxWidth().height(80.dp),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null, tint = PrimaryIndigo)
                    Text(t.selectImage, color = TextSecondary, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun SmallIconButton(icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(BackgroundDark.copy(alpha = 0.7f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DenoisingSlider(value: Float, onValueChange: (Float) -> Unit, t: AppText) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(t.denoisingStrength, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text("%.2f".format(value), color = PrimaryIndigo, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.height(4.dp))
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = 0.1f..1.0f,
            colors = SliderDefaults.colors(thumbColor = PrimaryIndigo, activeTrackColor = PrimaryIndigo, inactiveTrackColor = BorderDark)
        )
        Text(
            t.denoisingTip,
            color = TextTertiary,
            fontSize = 11.sp
        )
    }
}

@Composable
private fun PromptSection(
    prompt: String,
    negativePrompt: String,
    onPromptChange: (String) -> Unit,
    onNegativePromptChange: (String) -> Unit,
    t: AppText
) {
    var showPromptDialog by remember { mutableStateOf(false) }
    var showNegDialog by remember { mutableStateOf(false) }

    Column {
        // 正向提示词
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t.promptLabel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            IconButton(
                onClick = { showPromptDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "展开编辑", tint = TextTertiary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        OutlinedTextField(
            value = prompt,
            onValueChange = onPromptChange,
            placeholder = { Text(t.promptPlaceholder, color = TextTertiary) },
            modifier = Modifier.fillMaxWidth().height(100.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )
        Spacer(modifier = Modifier.height(10.dp))

        // 反向提示词
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t.negativePromptLabel, color = TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            IconButton(
                onClick = { showNegDialog = true },
                modifier = Modifier.size(28.dp)
            ) {
                Icon(Icons.Filled.OpenInFull, contentDescription = "展开编辑", tint = TextTertiary, modifier = Modifier.size(14.dp))
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedTextField(
            value = negativePrompt,
            onValueChange = onNegativePromptChange,
            placeholder = { Text(t.negativePromptPlaceholder, color = TextTertiary) },
            modifier = Modifier.fillMaxWidth().height(60.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = SurfaceDark,
                unfocusedContainerColor = SurfaceDark,
                focusedBorderColor = BorderDark,
                unfocusedBorderColor = BorderDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(12.dp)
        )
    }

    // 正向提示词展开对话框
    if (showPromptDialog) {
        PromptExpandDialog(
            title = t.promptLabel,
            value = prompt,
            onValueChange = onPromptChange,
            onDismiss = { showPromptDialog = false }
        )
    }

    // 反向提示词展开对话框
    if (showNegDialog) {
        PromptExpandDialog(
            title = t.negativePromptLabel,
            value = negativePrompt,
            onValueChange = onNegativePromptChange,
            onDismiss = { showNegDialog = false }
        )
    }
}

@Composable
private fun PromptExpandDialog(
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var editValue by remember { mutableStateOf(value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        containerColor = SurfaceDark,
        title = {
            Text(title, color = TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            OutlinedTextField(
                value = editValue,
                onValueChange = { editValue = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = BackgroundDark,
                    unfocusedContainerColor = BackgroundDark,
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = BorderDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary
                ),
                shape = RoundedCornerShape(12.dp)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    onValueChange(editValue)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("确定", color = Color.White, fontSize = 14.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = TextSecondary, fontSize = 14.sp)
            }
        }
    )
}

/**
 * 当前参数（只读标签）—— 把所有参数用一行/两行小标签展示
 * 实际调整在「参数」页（点击可跳过去）
 */
@Composable
private fun CurrentParamsLabel(
    width: Int,
    height: Int,
    steps: Int,
    cfgScale: Float,
    seed: Long,
    sampler: String,
    t: AppText
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        // 标题行
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Tune,
                contentDescription = null,
                tint = TextTertiary,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                t.paramsLabel,
                color = TextTertiary,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
        // 参数标签（多行小卡片）
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = SurfaceDark.copy(alpha = 0.6f),
            border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                ParamTagRow(
                    label = "尺寸",
                    value = "${width}×${height}"
                )
                ParamTagRow(
                    label = "步数",
                    value = "$steps 步 / CFG ${"%.1f".format(cfgScale)}"
                )
                ParamTagRow(
                    label = "采样",
                    value = sampler.replace("_", " ").uppercase()
                )
                ParamTagRow(
                    label = "种子",
                    value = if (seed == -1L) "随机" else seed.toString()
                )
            }
        }
    }
}

@Composable
private fun ParamTagRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextTertiary, fontSize = 11.sp)
        Text(value, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelSection(
    activeModel: String,
    models: List<String>,
    onSelectModel: (String) -> Unit,
    t: AppText
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(t.selectModel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        if (models.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Text(t.noModelTip, color = TextTertiary, fontSize = 13.sp)
            }
        } else {
            Box {
                // 下拉触发器
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                        .clickable { expanded = true }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ModelTraining,
                            contentDescription = null,
                            tint = if (activeModel.isNotEmpty()) PrimaryIndigo else TextTertiary,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = activeModel.ifEmpty { t.selectModel },
                            color = if (activeModel.isNotEmpty()) TextPrimary else TextTertiary,
                            fontSize = 13.sp,
                            fontWeight = if (activeModel.isNotEmpty()) FontWeight.Medium else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                    Icon(
                        Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                // 下拉菜单
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .background(SurfaceDark, RoundedCornerShape(12.dp))
                ) {
                    models.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.ModelTraining,
                                        contentDescription = null,
                                        tint = if (model == activeModel) PrimaryIndigo else TextSecondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        model,
                                        color = if (model == activeModel) PrimaryIndigo else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (model == activeModel) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            },
                            onClick = {
                                onSelectModel(model)
                                expanded = false
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GenerateButton(
    isGenerating: Boolean,
    isCancelling: Boolean,
    canGenerate: Boolean,
    onGenerate: () -> Unit,
    onCancel: () -> Unit,
    t: AppText
) {
    if (isGenerating || isCancelling) {
        // 生成/取消中的按钮
        Button(
            onClick = onCancel,
            enabled = !isCancelling,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = ErrorRed.copy(alpha = 0.9f),
                contentColor = Color.White
            )
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (isCancelling) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    Text(t.cancelling, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                } else {
                    Icon(Icons.Filled.Stop, contentDescription = null, tint = Color.White)
                    Text(t.stopGenerate, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        return
    }

    // 普通生成按钮
    Button(
        onClick = onGenerate,
        enabled = canGenerate,
        modifier = Modifier.fillMaxWidth().height(56.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            disabledContainerColor = PrimaryIndigo.copy(alpha = 0.3f)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = if (canGenerate) Brush.horizontalGradient(listOf(PrimaryIndigo, AccentPurple))
                    else Brush.horizontalGradient(listOf(PrimaryIndigo.copy(alpha = 0.3f), AccentPurple.copy(alpha = 0.3f))),
                    shape = RoundedCornerShape(14.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White)
                Text(t.startGenerate, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ProgressDetailSection(state: AppUiState, t: AppText) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    if (state.isDecoding) t.decoding else t.generating,
                    color = TextPrimary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "${state.currentStep}/${state.totalSteps}",
                    color = PrimaryIndigo,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { state.progressPercent / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = PrimaryIndigo,
                trackColor = BorderDark
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("${t.elapsed} ${state.elapsedTime}s", color = TextTertiary, fontSize = 11.sp)
                if (state.generationSpeed.isNotEmpty()) {
                    Text("${t.speed} ${state.generationSpeed}", color = TextTertiary, fontSize = 11.sp)
                }
                Text("${t.remaining} ~${state.estimatedLeftTime}s", color = TextTertiary, fontSize = 11.sp)
            }
            if (state.isCpuFallback) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = WarningYellow, modifier = Modifier.size(14.dp))
                    Text(t.cpuFallback, color = WarningYellow, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun ResultSection(
    state: AppUiState,
    context: Context,
    onCopy: (Bitmap) -> Unit,
    onSave: (Bitmap) -> Unit,
    onShare: (Bitmap) -> Unit,
    t: AppText
) {
    Column {
        Text(t.generatingResult, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = SurfaceDark),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(BackgroundDark),
                    contentAlignment = Alignment.Center
                ) {
                    val bitmap = state.outputImage
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "生成的图片",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Text(t.waitingGen, color = TextTertiary, fontSize = 13.sp)
                    }
                }
                if (bitmap_not_null(state)) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        MetaItem("Seed", state.outputSeed.toString())
                        MetaItem(t.duration, "%.1fs".format(state.genDuration))
                        MetaItem(t.size, "${state.width}×${state.height}")
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActionButton(icon = Icons.Filled.ContentCopy, label = t.copy, onClick = { state.outputImage?.let(onCopy) }, modifier = Modifier.weight(1f))
                        ActionButton(icon = Icons.Filled.Save, label = t.save, onClick = { state.outputImage?.let(onSave) }, modifier = Modifier.weight(1f))
                        ActionButton(icon = Icons.Filled.Share, label = t.share, onClick = { state.outputImage?.let(onShare) }, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

private fun bitmap_not_null(state: AppUiState) = state.outputImage != null

@Composable
private fun MetaItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextTertiary, fontSize = 10.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ActionButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderDark),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = null, tint = PrimaryIndigo, modifier = Modifier.size(16.dp))
            Text(label, fontSize = 12.sp)
        }
    }
}

// ==================== 工具函数 ====================

private fun copyBitmapToClipboard(context: Context, bitmap: Bitmap, t: AppText) {
    try {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("image", "已复制图像 (${bitmap.width}×${bitmap.height})")
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, t.imageCopied, Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "${t.copyFailed}：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

private fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    return try {
        val filename = "SD-AI_${System.currentTimeMillis()}.png"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ 用 MediaStore
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/SD-AI")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                context.contentResolver.openOutputStream(it)?.use { out: java.io.OutputStream ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
                }
            }
            true
        } else {
            // 旧版本：保存到公共目录
            @Suppress("DEPRECATION")
            val picturesDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_PICTURES)
            val sdaiDir = File(picturesDir, "SD-AI")
            sdaiDir.mkdirs()
            val file = File(sdaiDir, filename)
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 95, out)
            }
            true
        }
    } catch (e: Exception) {
        e.printStackTrace()
        false
    }
}

private fun shareBitmap(context: Context, bitmap: Bitmap, t: AppText) {
    try {
        val file = File(context.cacheDir, "share_${System.currentTimeMillis()}.png")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 95, out) }
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "分享图片"))
    } catch (e: Exception) {
        Toast.makeText(context, "${t.shareFailed}：${e.message}", Toast.LENGTH_SHORT).show()
    }
}

// ==================== 统一状态栏 ====================

@Composable
private fun UnifiedStatusBar(state: AppUiState) {
    val t = rememberText(state)
    val isLocal = state.useLocalInference

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isLocal) {
                // Local mode: show phone hardware
                val cpuVal = if (state.localCpuUsage >= 0) "${state.localCpuUsage.toInt()}%" else "--"
                TelemetryChip(
                    icon = Icons.Filled.Memory,
                    label = t.cpu,
                    value = cpuVal,
                    color = if (state.localCpuUsage > 80) WarningYellow else SuccessGreen
                )

                val ramPct = if (state.localRamTotalGb > 0)
                    (state.localRamUsedGb / state.localRamTotalGb * 100).toInt()
                else 0
                TelemetryChip(
                    icon = Icons.Filled.Storage,
                    label = t.ram,
                    value = "${state.localRamUsedGb.toInt()}GB",
                    subValue = if (state.localRamTotalGb > 0) "/ ${state.localRamTotalGb.toInt()}GB" else null,
                    color = if (ramPct > 90) WarningYellow else TextSecondary
                )

                // Local GPU
                val gpuVal = if (state.localGpuUsage >= 0) "${state.localGpuUsage.toInt()}%" else "--"
                TelemetryChip(
                    icon = Icons.Filled.Videocam,
                    label = "GPU",
                    value = gpuVal,
                    color = PrimaryIndigo
                )

                // CPU temp
                if (state.localCpuTemp >= 0) {
                    TelemetryChip(
                        icon = Icons.Filled.DeviceThermostat,
                        label = "Temp",
                        value = "${state.localCpuTemp.toInt()}°",
                        color = if (state.localCpuTemp > 70) WarningYellow else TextSecondary
                    )
                }
            } else if (state.isConnected) {
                // Remote PC mode: show PC hardware
                TelemetryChip(
                    icon = Icons.Filled.Memory,
                    label = t.cpu,
                    value = "${state.telemetryCpuUsage.toInt()}%",
                    color = if (state.telemetryCpuUsage > 80) WarningYellow else SuccessGreen
                )

                val ramPct = if (state.telemetryRamTotalGb > 0)
                    (state.telemetryRamUsedGb / state.telemetryRamTotalGb * 100).toInt()
                else 0
                TelemetryChip(
                    icon = Icons.Filled.Storage,
                    label = t.ram,
                    value = "${state.telemetryRamUsedGb.toInt()}GB",
                    subValue = if (state.telemetryRamTotalGb > 0) "/ ${state.telemetryRamTotalGb.toInt()}GB" else null,
                    color = if (ramPct > 90) WarningYellow else TextSecondary
                )

                // PC GPU utilization + VRAM
                if (state.telemetryHasGpu) {
                    val gpuLabel = if (state.telemetryGpuName.length > 10)
                        state.telemetryGpuName.take(10) + ".."
                    else state.telemetryGpuName
                    val gpuVal = if (state.telemetryGpuUsage > 0) "${state.telemetryGpuUsage.toInt()}%" else "--"
                    TelemetryChip(
                        icon = Icons.Filled.Videocam,
                        label = gpuLabel,
                        value = gpuVal,
                        subValue = if (state.telemetryVramTotalGb > 0)
                            "%.1f/%.1fGB".format(state.telemetryVramUsedGb, state.telemetryVramTotalGb)
                        else null,
                        color = PrimaryIndigo
                    )
                }
            } else {
                // Disconnected
                Text(
                    text = t.disconnected,
                    color = TextTertiary,
                    fontSize = 13.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun TelemetryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    subValue: String? = null,
    color: Color = TextSecondary,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = modifier
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
        Column {
            Text(label, color = TextTertiary, fontSize = 10.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, color = color, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (subValue != null) {
                    Text(subValue, color = TextTertiary, fontSize = 10.sp)
                }
            }
        }
    }
}

// ==================== 推理模式切换栏 ====================

@Composable
private fun InferenceModeBar(
    useLocal: Boolean,
    onToggle: () -> Unit,
    t: AppText
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .padding(4.dp)
    ) {
        ModeButton(
            label = t.remotePc,
            icon = Icons.Filled.Computer,
            isSelected = !useLocal,
            onClick = { if (useLocal) onToggle() },
            modifier = Modifier.weight(1f)
        )
        ModeButton(
            label = t.localMnn,
            icon = Icons.Filled.PhoneAndroid,
            isSelected = useLocal,
            onClick = { if (!useLocal) onToggle() },
            modifier = Modifier.weight(1f)
        )
    }
}

// ==================== 本地 MNN 模型选择 ====================

@Composable
private fun MnnModelSection(
    models: List<com.sdaiapp.inference.LocalModelInfo>,
    selected: String,
    isLoaded: Boolean,
    onSelect: (String) -> Unit,
    onLoad: () -> Unit,
    onRescan: () -> Unit,
    t: AppText
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(t.localMnnModel, color = TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("${models.size} ${t.mnnCount}", color = TextTertiary, fontSize = 11.sp)
                IconButton(onClick = onRescan, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Refresh, contentDescription = "重新扫描", tint = TextSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (models.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(t.noMnnModel, color = TextTertiary, fontSize = 13.sp)
                    Text(
                        t.mnnDirTip,
                        color = TextTertiary,
                        fontSize = 11.sp
                    )
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                models.forEach { model ->
                    val isSel = model.name == selected
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSel) PrimaryIndigo.copy(alpha = 0.15f) else SurfaceDark
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, if (isSel) PrimaryIndigo else BorderDark, RoundedCornerShape(10.dp))
                            .clickable { onSelect(model.name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    Icons.Filled.Memory,
                                    contentDescription = null,
                                    tint = if (isSel) PrimaryIndigo else TextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Column {
                                    Text(
                                        model.name,
                                        color = if (isSel) PrimaryIndigo else TextPrimary,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSel) FontWeight.Medium else FontWeight.Normal
                                    )
                                    // 子模型状态标签
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        ModelTag("UNet", model.hasUnet)
                                        ModelTag("VAE", model.hasVae)
                                        ModelTag("Text", model.hasTextEncoder)
                                        Text(
                                            formatBytes(model.sizeBytes),
                                            color = TextTertiary,
                                            fontSize = 10.sp,
                                            modifier = Modifier.padding(start = 4.dp)
                                        )
                                    }
                                }
                            }
                            if (isSel) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "已选择", tint = PrimaryIndigo, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // 加载按钮
            val selectedModel = models.find { it.name == selected }
            val canLoad = selected.isNotEmpty() && !isLoaded && selectedModel?.isComplete == true

            Button(
                onClick = onLoad,
                enabled = canLoad,
                modifier = Modifier.fillMaxWidth().height(40.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isLoaded) SuccessGreen.copy(alpha = 0.2f) else PrimaryIndigo,
                    disabledContainerColor = SurfaceDark
                )
            ) {
                Icon(
                    if (isLoaded) Icons.Filled.CheckCircle else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    tint = if (isLoaded) SuccessGreen else Color.White,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    when {
                        isLoaded -> "${t.loaded}：${selected}"
                        selectedModel != null && !selectedModel.isComplete -> t.modelIncomplete
                        else -> t.loadModel
                    },
                    color = if (isLoaded) SuccessGreen else Color.White,
                    fontSize = 13.sp
                )
            }
        }
    }
}

/** 子模型状态标签（绿色=有，红色=缺） */
@Composable
private fun ModelTag(label: String, exists: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(
                if (exists) SuccessGreen.copy(alpha = 0.15f)
                else WarningYellow.copy(alpha = 0.15f)
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            label,
            color = if (exists) SuccessGreen else WarningYellow,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatBytes(bytes: Long): String = when {
    bytes < 1024 -> "$bytes B"
    bytes < 1024 * 1024 -> "${bytes / 1024} KB"
    bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
    else -> String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024))
}

// 工具：占位（不要在生成按钮里调用）
