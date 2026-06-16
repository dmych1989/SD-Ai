package com.sdaiapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.MainViewModel

@Composable
fun ConstraintsScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .verticalScroll(scrollState)
            .padding(16.dp)
    ) {
        // Header
        Text(
            text = "生成参数",
            color = TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "调整图像生成的各项参数",
            color = TextSecondary,
            fontSize = 14.sp
        )
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Resolution Section
        ConstraintCard(title = "分辨率") {
            ResolutionSelector(
                currentWidth = uiState.width,
                currentHeight = uiState.height,
                onResolutionSelected = { w, h ->
                    viewModel.updateWidth(w)
                    viewModel.updateHeight(h)
                }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Generation Parameters
        ConstraintCard(title = "生成参数") {
            StepperRow(
                label = "步数",
                value = uiState.steps,
                min = 1,
                max = 100,
                step = 1,
                hint = "步数越多 = 质量越高，速度越慢",
                onValueChange = viewModel::updateSteps
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            StepperRow(
                label = "CFG Scale",
                value = uiState.cfgScale,
                min = 1f,
                max = 30f,
                step = 0.5f,
                displayValue = String.format("%.1f", uiState.cfgScale),
                hint = "值越高 = 更贴近提示词",
                onValueChange = viewModel::updateCfgScale
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            StepperRow(
                label = "种子",
                value = uiState.seed,
                min = -1L,
                max = 999999L,
                step = 1L,
                displayValue = if (uiState.seed == -1L) "" else uiState.seed.toString(),
                hint = "默认 -1 = 每次随机生成；填具体数字 = 用同一种子复现",
                onValueChange = viewModel::updateSeed,
                randomButton = true
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            SamplerSelector(
                currentSampler = uiState.sampler,
                onSamplerSelected = { viewModel.updateSampler(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Backend & Performance
        ConstraintCard(title = "后端 & 性能") {
            ToggleRow(
                label = "使用 GPU",
                hint = "启用硬件加速 (CUDA/Vulkan)",
                checked = uiState.useGpu,
                onCheckedChange = { viewModel.updateUseGpu(it) }
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            StepperRow(
                label = "CPU 线程数",
                value = uiState.threads,
                min = 1,
                max = 32,
                step = 1,
                hint = "仅在 CPU 运行时使用",
                onValueChange = { viewModel.updateThreads(it) }
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            ToggleRow(
                label = "TAESD 解码器",
                hint = "更快的 VAE 解码，轻微质量损失",
                checked = uiState.useTaesd,
                onCheckedChange = { viewModel.updateUseTaesd(it) }
            )
            
            Divider(
                color = DividerDark,
                modifier = Modifier.padding(vertical = 12.dp)
            )
            
            ToggleRow(
                label = "Flash Attention",
                hint = "内存高效注意力机制 (推荐)",
                checked = uiState.useFlashAttn,
                onCheckedChange = { viewModel.updateUseFlashAttn(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // img2img Settings
        ConstraintCard(title = "img2img 设置") {
            StepperRow(
                label = "去噪强度",
                value = uiState.denoisingStrength,
                min = 0.1f,
                max = 1f,
                step = 0.05f,
                displayValue = String.format("%.2f", uiState.denoisingStrength),
                hint = "值越低 = 更接近原图；值越高 = 更具创意",
                onValueChange = { viewModel.updateDenoisingStrength(it) }
            )
        }
        
        Spacer(modifier = Modifier.height(80.dp))
    }
}

@Composable
private fun ConstraintCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceDark)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(16.dp))
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ResolutionSelector(
    currentWidth: Int,
    currentHeight: Int,
    onResolutionSelected: (Int, Int) -> Unit
) {
    val resolutions = listOf(
        "256×256" to Pair(256, 256),
        "384×384" to Pair(384, 384),  // 【推荐】手机默认尺寸
        "512×512" to Pair(512, 512),
        "512×768" to Pair(512, 768),
        "768×512" to Pair(768, 512),
        "768×768" to Pair(768, 768),
        "640×1024" to Pair(640, 1024),
        "1024×640" to Pair(1024, 640),
        "1024×1024" to Pair(1024, 1024),
    )
    val currentLabel = "${currentWidth}×${currentHeight}"
    val isCustom = resolutions.none { it.second.first == currentWidth && it.second.second == currentHeight }
    val displayLabel = if (isCustom) "自定义 $currentLabel" else currentLabel

    var expanded by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(isCustom) }

    Column {
        Text(
            text = "预设分辨率",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))

        // 下拉菜单
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded }
        ) {
            OutlinedTextField(
                value = displayLabel,
                onValueChange = {},
                readOnly = true,
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                shape = RoundedCornerShape(10.dp),
                trailingIcon = {
                    Icon(
                        if (expanded) Icons.Filled.ArrowDropUp
                        else Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = PrimaryIndigo
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DividerDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedContainerColor = SurfaceLight,
                    unfocusedContainerColor = SurfaceLight,
                    cursorColor = PrimaryIndigo
                ),
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                resolutions.forEach { (label, res) ->
                    val isSelected = currentWidth == res.first && currentHeight == res.second
                    DropdownMenuItem(
                        text = {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, fontSize = 14.sp)
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = PrimaryIndigo,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        onClick = {
                            onResolutionSelected(res.first, res.second)
                            showCustomInput = false
                            expanded = false
                        }
                    )
                }
                HorizontalDivider(color = DividerDark)
                DropdownMenuItem(
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.Edit,
                                contentDescription = null,
                                tint = PrimaryIndigo,
                                modifier = Modifier.size(16.dp)
                            )
                            Text("自定义…", fontSize = 14.sp, color = PrimaryIndigo)
                        }
                    },
                    onClick = {
                        showCustomInput = true
                        expanded = false
                    }
                )
            }
        }

        // 自定义输入（点了"自定义"才显示）
        if (showCustomInput) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NumberInput(
                    label = "宽 (px)",
                    value = currentWidth,
                    onValueChange = { w -> onResolutionSelected(w.coerceIn(64, 2048), currentHeight) },
                    modifier = Modifier.weight(1f)
                )
                NumberInput(
                    label = "高 (px)",
                    value = currentHeight,
                    onValueChange = { h -> onResolutionSelected(currentWidth, h.coerceIn(64, 2048)) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun NumberInput(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        
        OutlinedTextField(
            value = value.toString(),
            onValueChange = { 
                val num = it.toIntOrNull()
                if (num != null && num >= 64) onValueChange(num)
            },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = PrimaryIndigo,
                unfocusedBorderColor = DividerDark,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = PrimaryIndigo
            ),
            singleLine = true
        )
    }
}

@Composable
private fun <T : Number> StepperRow(
    label: String,
    value: T,
    min: T,
    max: T,
    step: T,
    displayValue: String? = null,
    hint: String? = null,
    onValueChange: (T) -> Unit,
    randomButton: Boolean = false
) where T : Comparable<T> {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (randomButton) {
                TextButton(
                    onClick = {
                        @Suppress("UNCHECKED_CAST")
                        onValueChange(min)
                    },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Casino,
                        contentDescription = "随机",
                        tint = PrimaryIndigo,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("随机", color = PrimaryIndigo, fontSize = 13.sp)
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Decrease button
            IconButton(
                onClick = {
                    val newValue = when (value) {
                        is Int -> {
                            val cur = value.toInt()
                            // -1 是"随机"占位，按 - 应该保持 -1 而不是 -2
                            if (cur <= min.toInt()) min
                            else maxOf(min.toInt(), cur - step.toInt()) as T
                        }
                        is Float -> maxOf(min.toFloat(), (value.toFloat() - step.toFloat())) as T
                        is Long -> {
                            val cur = value.toLong()
                            if (cur <= min.toLong()) min
                            else maxOf(min.toLong(), cur - step.toLong()) as T
                        }
                        else -> value
                    }
                    onValueChange(newValue)
                },
                enabled = when (value) {
                    is Int -> value.toInt() > min.toInt()
                    is Float -> value.toFloat() > min.toFloat()
                    is Long -> value.toLong() > min.toLong()
                    else -> true
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLight)
            ) {
                Text(
                    text = "−",
                    color = if (value.compareTo(min) > 0) TextPrimary else TextTertiary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Manual input
            OutlinedTextField(
                value = displayValue ?: value.toString(),
                onValueChange = { txt ->
                    val newValue: T? = when (value) {
                        is Int -> txt.toIntOrNull()?.let { it.coerceIn(min.toInt(), max.toInt()) } as T?
                        is Float -> txt.toFloatOrNull()?.let { it.coerceIn(min.toFloat(), max.toFloat()) } as T?
                        is Long -> txt.toLongOrNull()?.let { it.coerceIn(min.toLong(), max.toLong()) } as T?
                        else -> null
                    }
                    if (newValue != null) onValueChange(newValue)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(8.dp),
                placeholder = {
                    if (displayValue != null && displayValue.isEmpty()) {
                        Text(
                            "输入数字或 -1",
                            color = TextTertiary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = SurfaceDarker,
                    unfocusedContainerColor = SurfaceDarker,
                    focusedBorderColor = PrimaryIndigo,
                    unfocusedBorderColor = DividerDark,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = PrimaryIndigo
                ),
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                )
            )

            // Increase button
            IconButton(
                onClick = {
                    val newValue = when (value) {
                        is Int -> minOf(max.toInt(), (value.toInt() + step.toInt())) as T
                        is Float -> minOf(max.toFloat(), (value.toFloat() + step.toFloat())) as T
                        is Long -> minOf(max.toLong(), (value.toLong() + step.toLong())) as T
                        else -> value
                    }
                    onValueChange(newValue)
                },
                enabled = when (value) {
                    is Int -> value.toInt() < max.toInt()
                    is Float -> value.toFloat() < max.toFloat()
                    is Long -> value.toLong() < max.toLong()
                    else -> true
                },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLight)
            ) {
                Text(
                    text = "+",
                    color = if (value.compareTo(max) < 0) TextPrimary else TextTertiary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (hint != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = hint,
                color = TextTertiary,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ToggleRow(
    label: String,
    hint: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                color = TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
            if (hint != null) {
                Text(
                    text = hint,
                    color = TextTertiary,
                    fontSize = 12.sp
                )
            }
        }
        
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = PrimaryIndigo,
                checkedTrackColor = PrimaryIndigo.copy(alpha = 0.5f),
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = SurfaceLight
            )
        )
    }
}

@Composable
private fun SamplerSelector(
    currentSampler: String,
    onSamplerSelected: (String) -> Unit
) {
    val samplers = listOf(
        "euler_a", "euler", "heun", "dpm2", "dpm++2s_a",
        "dpm++2m", "dpm++2mv2", "lcm", "ddim"
    )
    
    Column {
        Text(
            text = "采样方法",
            color = TextSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            samplers.forEach { sampler ->
                val isSelected = currentSampler == sampler
                
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSamplerSelected(sampler) },
                    color = if (isSelected) PrimaryIndigoContainer else SurfaceLight,
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        text = sampler,
                        color = if (isSelected) OnPrimaryIndigoContainer else TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
