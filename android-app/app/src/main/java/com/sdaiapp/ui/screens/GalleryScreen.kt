package com.sdaiapp.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.GalleryFilter
import com.sdaiapp.ui.viewmodel.MainViewModel
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GalleryScreen(
    viewModel: MainViewModel,
    onSendToImg2Img: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val filteredImages = remember(uiState.galleryImages, uiState.galleryFilter) {
        viewModel.getFilteredGallery()
    }
    var showFilterMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundDark)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "图库",
                        color = TextPrimary,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.gallerySelectionMode) {
                        Spacer(modifier = Modifier.width(8.dp))
                        // 蓝色徽章显示已选数量
                        Surface(
                            color = PrimaryIndigo,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "${uiState.gallerySelectedIds.size} / ${filteredImages.size}",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                Text(
                    text = if (uiState.galleryFilter == GalleryFilter.ALL)
                        "${uiState.galleryImages.size} 张图片"
                    else
                        "${filteredImages.size} / ${uiState.galleryImages.size} 张 (${filterDisplayName(uiState.galleryFilter)})",
                    color = TextSecondary,
                    fontSize = 14.sp
                )
            }

            // 右上角按钮组
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (uiState.gallerySelectionMode) {
                    // 批量选择模式按钮：全选 / 取消
                    IconButton(
                        onClick = {
                            if (uiState.gallerySelectedIds.size == filteredImages.size) {
                                viewModel.setGallerySelectionMode(false)
                            } else {
                                viewModel.selectAllGallery()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(PrimaryIndigo.copy(alpha = 0.2f))
                    ) {
                        Icon(
                            if (uiState.gallerySelectedIds.size == filteredImages.size && filteredImages.isNotEmpty())
                                Icons.Filled.Deselect else Icons.Filled.SelectAll,
                            contentDescription = "全选/取消全选",
                            tint = PrimaryIndigo
                        )
                    }
                    IconButton(
                        onClick = {
                            if (uiState.gallerySelectedIds.isNotEmpty()) {
                                viewModel.deleteSelectedGalleryItems()
                            }
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (uiState.gallerySelectedIds.isEmpty()) ErrorRed.copy(alpha = 0.2f)
                                else ErrorRed
                            )
                    ) {
                        Icon(
                            Icons.Filled.Delete,
                            contentDescription = "删除选中",
                            tint = if (uiState.gallerySelectedIds.isEmpty()) ErrorRed.copy(alpha = 0.5f) else Color.White
                        )
                    }
                    IconButton(
                        onClick = { viewModel.setGallerySelectionMode(false) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "退出选择", tint = TextSecondary)
                    }
                } else {
                    // 正常模式按钮：筛选 + 批量删除
                    Box {
                        IconButton(
                            onClick = { showFilterMenu = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (uiState.galleryFilter != GalleryFilter.ALL) PrimaryIndigo.copy(alpha = 0.2f)
                                    else SurfaceDark
                                )
                        ) {
                            Icon(
                                Icons.Filled.FilterList,
                                contentDescription = "筛选",
                                tint = if (uiState.galleryFilter != GalleryFilter.ALL) PrimaryIndigo else TextSecondary
                            )
                        }
                        // 筛选下拉菜单
                        DropdownMenu(
                            expanded = showFilterMenu,
                            onDismissRequest = { showFilterMenu = false }
                        ) {
                            GalleryFilter.values().forEach { filter ->
                                DropdownMenuItem(
                                    text = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(filterDisplayName(filter), fontSize = 14.sp)
                                            if (uiState.galleryFilter == filter) {
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Icon(Icons.Filled.Check, null, tint = PrimaryIndigo, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    },
                                    onClick = {
                                        viewModel.setGalleryFilter(filter)
                                        showFilterMenu = false
                                    }
                                )
                            }
                        }
                    }
                    IconButton(
                        onClick = { viewModel.setGallerySelectionMode(true) },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                    ) {
                        Icon(Icons.Filled.DeleteSweep, contentDescription = "批量删除", tint = ErrorRed)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        if (filteredImages.isEmpty()) {
            EmptyGalleryState(filter = uiState.galleryFilter)
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredImages) { image ->
                    GalleryImageCard(
                        image = image,
                        isSelectionMode = uiState.gallerySelectionMode,
                        isSelected = image.id in uiState.gallerySelectedIds,
                        onClick = {
                            if (uiState.gallerySelectionMode) {
                                viewModel.toggleGallerySelection(image.id)
                            } else {
                                onSendToImg2Img(image.imagePath)
                            }
                        },
                        onSendToImg2Img = { onSendToImg2Img(image.imagePath) },
                        onDelete = { viewModel.deleteGalleryItem(image.id) }
                    )
                }
            }
        }
    }
}

private fun filterDisplayName(filter: GalleryFilter): String = when (filter) {
    GalleryFilter.ALL -> "全部"
    GalleryFilter.LAST_7_DAYS -> "最近 7 天"
    GalleryFilter.HIGH_RES -> "高清 (≥768px)"
    GalleryFilter.FAVORITES -> "收藏"
}

@Composable
private fun EmptyGalleryState(filter: GalleryFilter) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(SurfaceDark),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoLibrary,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(48.dp)
                )
            }
            Text(
                text = if (filter == GalleryFilter.ALL) "图库为空" else "没有匹配的图",
                color = TextSecondary, fontSize = 16.sp, fontWeight = FontWeight.Medium
            )
            Text(
                text = if (filter == GalleryFilter.ALL) "生成的图片将显示在这里"
                else "试试切换其他筛选条件",
                color = TextTertiary, fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun GalleryImageCard(
    image: com.sdaiapp.data.model.HistoryItem,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    onSendToImg2Img: () -> Unit,
    onDelete: () -> Unit
) {
    val borderColor = if (isSelected) PrimaryIndigo
    else androidx.compose.ui.graphics.Color.Transparent
    val bg = if (isSelected) PrimaryIndigo.copy(alpha = 0.08f) else SurfaceDark

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = bg),
        border = androidx.compose.foundation.BorderStroke(2.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            // 上半部分：左边图片 + 选中标记，右边按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 左侧：缩略图 + 选中标记
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    val file = File(image.imagePath)
                    if (file.exists()) {
                        AsyncImage(
                            model = file,
                            contentDescription = "生成图片",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = TextTertiary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    // 选中模式右上角显示勾选圆圈
                    if (isSelectionMode) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(4.dp)
                                .size(22.dp)
                                .clip(RoundedCornerShape(11.dp))
                                .background(if (isSelected) PrimaryIndigo else Color.Black.copy(alpha = 0.5f)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSelected) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }
                }

                // 右侧：按钮组
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = onSendToImg2Img,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(32.dp),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PrimaryIndigo)
                    ) {
                        Icon(Icons.Filled.Transform, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("图生图", fontSize = 12.sp)
                    }
                    if (!isSelectionMode) {
                        // 普通模式才显示删除按钮（选择模式下删除通过右上角批量按钮）
                        Button(
                            onClick = onDelete,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ErrorRed.copy(alpha = 0.2f),
                                contentColor = ErrorRed
                            )
                        ) {
                            Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("删除", fontSize = 12.sp)
                        }
                    } else {
                        // 选择模式显示状态文字
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) PrimaryIndigo.copy(alpha = 0.2f)
                                    else SurfaceLight
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (isSelected) "✓ 已选中" else "点击选中",
                                color = if (isSelected) PrimaryIndigo else TextTertiary,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 下半部分：参数信息
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                // 提示词
                Text(
                    text = image.prompt,
                    color = TextPrimary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 参数行
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    InfoChip("${image.params.width}×${image.params.height}")
                    InfoChip("${image.params.steps}步")
                    InfoChip("CFG${"%.1f".format(image.params.cfgScale)}")
                    InfoChip("s${image.params.seed}")
                }

                // 模型名行（如果存在）
                if (image.params.modelName.isNotBlank()) {
                    val modelDisplay = image.params.modelName
                        .substringAfterLast('/')
                        .substringAfterLast('\\')
                        .removeSuffix(".safetensors")
                        .removeSuffix(".mnn")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.AutoAwesome,
                            contentDescription = null,
                            tint = AccentPurple,
                            modifier = Modifier.size(10.dp)
                        )
                        Text(
                            text = modelDisplay,
                            color = AccentPurple,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // 时间
                val df = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
                Text(
                    text = df.format(Date(image.timestamp)),
                    color = TextTertiary,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Surface(
        color = SurfaceLight,
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = text,
            color = TextSecondary,
            fontSize = 9.sp,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
        )
    }
}
