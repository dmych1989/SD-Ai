package com.sdaiapp.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.theme.*

// 扩展的频道配置，包含完整参数
data class ChannelConfig(
    val id: String,
    val name: String,
    val icon: String = "🎨",
    val description: String,
    val modelName: String? = null,
    // 分辨率
    val width: Int = 512,
    val height: Int = 512,
    // 生成参数
    val steps: Int = 20,
    val cfgScale: Float = 7.0f,
    val sampler: String = "euler_a",
    val seed: Int = -1,
    // 高级参数
    val useGpu: Boolean = true,
    val useTaesd: Boolean = true,
    val useFlashAttn: Boolean = true,
    val vaeTiling: Boolean = false,
    val vaeOnCpu: Boolean = false,
    val threads: Int = 4,
    val isDefault: Boolean = false
)

// 采样器显示名映射
fun samplerDisplayName(value: String): String = when (value) {
    "euler_a" -> "Euler A"
    "euler" -> "Euler"
    "heun" -> "Heun"
    "dpm2" -> "DPM2"
    "dpm++2s_a" -> "DPM++ 2S A"
    "dpm++2m" -> "DPM++ 2M"
    "lcm" -> "LCM"
    "ddim_trailing" -> "DDIM"
    else -> value
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChannelsScreen(
    viewModel: com.sdaiapp.ui.viewmodel.MainViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    // 频道列表 & 当前激活的频道 —— 数据来源改用 ViewModel（保证保存+全局生效）
    val channels = uiState.channels
    val selectedChannelId = uiState.activeChannelId
    var showCreateDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf<ChannelConfig?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Header - 更紧凑
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "频道管理",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "预设配置，一键切换生成参数",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }
            IconButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(PrimaryIndigo)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "创建频道", tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }

        // Channels List - 使用更小间距
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(channels) { channel ->
                ChannelCard(
                    channel = channel,
                    isSelected = channel.id == selectedChannelId,
                    // 【关键】激活频道：把 channel 的所有参数应用到当前生成状态
                    onSelect = {
                        viewModel.activateChannel(channel.id)
                    },
                    onEdit = { showEditDialog = channel },
                    onDelete = {
                        if (!channel.isDefault) {
                            viewModel.deleteChannel(channel.id)
                        }
                    }
                )
            }

            // 说明卡片 - 更紧凑
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceDark)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Icon(
                            Icons.Filled.Info,
                            contentDescription = null,
                            tint = PrimaryIndigo,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "频道保存了一套生成参数（分辨率、步数、采样器等）。\n选择后生成页面自动应用。",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(80.dp)) }
        }
    }

    // 创建/编辑对话框
    if (showCreateDialog || showEditDialog != null) {
        ChannelEditDialog(
            channel = showEditDialog,
            onDismiss = { showCreateDialog = false; showEditDialog = null },
            onSave = { newChannel ->
                // 【关键修复】保存到 ViewModel 而不是仅修改本地 var
                // 之前用 `var channels by remember`，数据只活在 Composable 里，
                // 一旦屏幕重绘或切走就丢失，所以"无法保存"。
                if (showEditDialog != null) {
                    // 编辑现有：用原 id 覆盖
                    viewModel.saveChannel(newChannel.copy(id = showEditDialog!!.id))
                } else {
                    // 新建：自动生成 id
                    val newId = "ch_" + System.currentTimeMillis()
                    viewModel.saveChannel(newChannel.copy(id = newId))
                    viewModel.activateChannel(newId)  // 新建后自动激活
                }
                showCreateDialog = false
                showEditDialog = null
            }
        )
    }
}

// ========== 优化后的频道卡片（更紧凑）==========
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChannelCard(
    channel: ChannelConfig,
    isSelected: Boolean,
    onSelect: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) PrimaryIndigo.copy(alpha = 0.12f) else SurfaceDark
        ),
        border = if (isSelected) BorderStroke(1.dp, PrimaryIndigo) else null
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // 第一行：图标 + 名称 + 标签 + 操作按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(channel.icon, fontSize = 20.sp)
                    Column {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = channel.name,
                                color = if (isSelected) PrimaryIndigo else TextPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (channel.isDefault) {
                                Text(
                                    "默认",
                                    color = AccentPurple,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentPurple.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                            if (isSelected) {
                                Text(
                                    "使用中",
                                    color = PrimaryIndigo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(PrimaryIndigo.copy(alpha = 0.2f))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                )
                            }
                        }
                        Text(channel.description, color = TextSecondary, fontSize = 11.sp, maxLines = 1)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "收起" else "展开",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = TextSecondary, modifier = Modifier.size(14.dp))
                    }
                    if (!channel.isDefault) {
                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(30.dp)
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除", tint = ErrorRed, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // 核心参数摘要行（始终显示，使用 FlowRow 自动换行）
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                SummaryChip(text = "${channel.width}×${channel.height}")
                SummaryChip(text = "${channel.steps}步")
                SummaryChip(text = "CFG ${channel.cfgScale}")
                SummaryChip(text = samplerDisplayName(channel.sampler))
            }

            // 展开区域：详细参数（可折叠）
            AnimatedVisibility(visible = expanded) {
                Column {
                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = DividerDark, thickness = 0.5.dp)
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (channel.useGpu) SummaryChip(text = "GPU") else SummaryChip(text = "CPU")
                        if (channel.useTaesd) SummaryChip(text = "TAESD")
                        if (channel.useFlashAttn) SummaryChip(text = "FlashAttn")
                        if (channel.vaeTiling) SummaryChip(text = "VAE平铺")
                        SummaryChip(text = "线程${channel.threads}")
                        if (channel.seed == -1) SummaryChip(text = "种子随机") else {
                            val seedStr = channel.seed.toString()
                            SummaryChip(text = if (seedStr.length > 6) "种子${seedStr.take(6)}.." else "种子${channel.seed}")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String) {
    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 11.sp,
            maxLines = 1,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ========== 优化后的编辑对话框 ==========

@Composable
private fun ChannelEditDialog(
    channel: ChannelConfig?,
    onDismiss: () -> Unit,
    onSave: (ChannelConfig) -> Unit
) {
    var name by remember { mutableStateOf(channel?.name ?: "") }
    var description by remember { mutableStateOf(channel?.description ?: "") }
    var icon by remember { mutableStateOf(channel?.icon ?: "🎨") }
    var width by remember { mutableStateOf((channel?.width ?: 512).toString()) }
    var height by remember { mutableStateOf((channel?.height ?: 512).toString()) }
    var steps by remember { mutableStateOf((channel?.steps ?: 20).toString()) }
    var cfgScale by remember { mutableStateOf((channel?.cfgScale ?: 7.0f).toString()) }
    var sampler by remember { mutableStateOf(channel?.sampler ?: "euler_a") }
    var useGpu by remember { mutableStateOf(channel?.useGpu ?: true) }
    var useTaesd by remember { mutableStateOf(channel?.useTaesd ?: true) }
    var useFlashAttn by remember { mutableStateOf(channel?.useFlashAttn ?: true) }
    var vaeTiling by remember { mutableStateOf(channel?.vaeTiling ?: false) }
    var threads by remember { mutableStateOf((channel?.threads ?: 4).toString()) }
    var seed by remember { mutableStateOf((channel?.seed ?: -1).toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(if (channel == null) "创建频道" else "编辑频道", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // 基本信息
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceLight),
                        contentAlignment = Alignment.Center
                    ) { Text(icon, fontSize = 18.sp) }
                    Column(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("名称") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                        )
                    }
                }
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("描述") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    singleLine = true,
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp)
                )

                Divider(color = DividerDark, thickness = 0.5.dp)

                // 分辨率 + 步数 + CFG 两行
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = width, onValueChange = { width = it.filter { c -> c.isDigit() } }, label = { Text("宽") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                    OutlinedTextField(value = height, onValueChange = { height = it.filter { c -> c.isDigit() } }, label = { Text("高") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(value = steps, onValueChange = { steps = it.filter { c -> c.isDigit() } }, label = { Text("步数") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                    OutlinedTextField(value = cfgScale, onValueChange = { cfgScale = it }, label = { Text("CFG") }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(10.dp), singleLine = true, textStyle = LocalTextStyle.current.copy(fontSize = 13.sp))
                }

                // 采样器
                Text("采样方法", color = TextSecondary, fontSize = 12.sp)
                var samplerExpanded by remember { mutableStateOf(false) }
                val samplers = listOf("euler_a","euler","heun","dpm2","dpm++2m","dpm++2s_a","lcm","ddim_trailing")
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceLight)
                            .border(BorderStroke(1.dp, DividerDark), RoundedCornerShape(10.dp))
                            .clickable { samplerExpanded = true }
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = samplerDisplayName(sampler),
                            color = TextPrimary,
                            fontSize = 13.sp
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = samplerExpanded, onDismissRequest = { samplerExpanded = false }) {
                        samplers.forEach {
                            DropdownMenuItem(
                                text = { Text(samplerDisplayName(it), fontSize = 13.sp) },
                                onClick = { sampler = it; samplerExpanded = false }
                            )
                        }
                    }
                }

                Divider(color = DividerDark, thickness = 0.5.dp)

                // 开关行 - 两列布局更紧凑
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Switch(checked = useGpu, onCheckedChange = { useGpu = it }, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo), modifier = Modifier.scale(0.85f))
                            Text(if (useGpu) "GPU" else "CPU", color = TextPrimary, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Switch(checked = useTaesd, onCheckedChange = { useTaesd = it }, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo), modifier = Modifier.scale(0.85f))
                            Text("TAESD", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Switch(checked = useFlashAttn, onCheckedChange = { useFlashAttn = it }, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo), modifier = Modifier.scale(0.85f))
                            Text("FlashAttn", color = TextPrimary, fontSize = 12.sp)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Switch(checked = vaeTiling, onCheckedChange = { vaeTiling = it }, colors = SwitchDefaults.colors(checkedThumbColor = PrimaryIndigo), modifier = Modifier.scale(0.85f))
                            Text("VAE平铺", color = TextPrimary, fontSize = 12.sp)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("线程", color = TextSecondary, fontSize = 12.sp)
                        Spacer(Modifier.weight(1f))
                        OutlinedTextField(
                            value = threads,
                            onValueChange = { threads = it.filter { c -> c.isDigit() } },
                            modifier = Modifier.width(64.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = DividerDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = PrimaryIndigo
                            )
                        )
                        Text("种子(-1=随机)", color = TextSecondary, fontSize = 12.sp)
                        OutlinedTextField(
                            value = seed,
                            onValueChange = { seed = it.filter { c -> c.isDigit() || it == "-" } },
                            modifier = Modifier.width(80.dp),
                            shape = RoundedCornerShape(8.dp),
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontSize = 13.sp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = PrimaryIndigo,
                                unfocusedBorderColor = DividerDark,
                                focusedTextColor = TextPrimary,
                                unfocusedTextColor = TextPrimary,
                                cursorColor = PrimaryIndigo
                            )
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        ChannelConfig(
                            id = channel?.id ?: "",
                            name = name,
                            icon = icon,
                            description = description,
                            width = width.toIntOrNull() ?: 512,
                            height = height.toIntOrNull() ?: 512,
                            steps = steps.toIntOrNull() ?: 20,
                            cfgScale = cfgScale.toFloatOrNull() ?: 7.0f,
                            sampler = sampler,
                            useGpu = useGpu,
                            useTaesd = useTaesd,
                            useFlashAttn = useFlashAttn,
                            vaeTiling = vaeTiling,
                            threads = threads.toIntOrNull() ?: 4,
                            seed = seed.toIntOrNull() ?: -1,
                            isDefault = channel?.isDefault ?: false
                        )
                    )
                },
                enabled = name.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo),
                shape = RoundedCornerShape(10.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)
            ) { Text("保存", fontSize = 14.sp) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)) { Text("取消", fontSize = 14.sp) }
        }
    )
}
