package com.sdaiapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sdaiapp.ui.screens.*
import com.sdaiapp.ui.theme.*
import com.sdaiapp.ui.viewmodel.MainViewModel
import com.sdaiapp.ui.viewmodel.AppUiState

sealed class Screen(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    object Generate : Screen("generate", "生成", Icons.Filled.Create)
    object Models : Screen("models", "模型", Icons.Filled.Memory)
    object Channels : Screen("channels", "频道", Icons.Filled.Bookmark)
    object Constraints : Screen("constraints", "参数", Icons.Filled.Tune)
    object Gallery : Screen("gallery", "图库", Icons.Filled.PhotoLibrary)
    object Settings : Screen("settings", "设置", Icons.Filled.Settings)
}

@Composable
fun AppNavigation(
    viewModel: MainViewModel
) {
    var currentScreen by remember { mutableStateOf<Screen>(Screen.Generate) }
    var showLogViewer by remember { mutableStateOf(false) }
    val uiState by viewModel.uiState.collectAsState()

    if (showLogViewer) {
        LogViewerScreen(onBack = { showLogViewer = false })
        return
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // Sidebar Navigation
        SidebarNavigation(
            currentScreen = currentScreen,
            onScreenSelected = { currentScreen = it },
            uiState = uiState
        )

        // Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            when (currentScreen) {
                Screen.Generate -> GenerateScreen(viewModel = viewModel)
                Screen.Models -> ModelsScreen(viewModel = viewModel)
                Screen.Channels -> ChannelsScreen(viewModel = viewModel)
                Screen.Constraints -> ConstraintsScreen(viewModel = viewModel)
                Screen.Gallery -> GalleryScreen(
                    viewModel = viewModel,
                    onSendToImg2Img = { imagePath ->
                        viewModel.setBaseImage(null, null)
                        // 将图片路径设置为基础图（图生图模式）
                        viewModel.setBaseImageFromPath(imagePath)
                    }
                )
                Screen.Settings -> SettingsScreen(
                    viewModel = viewModel,
                    onOpenLogViewer = { showLogViewer = true }
                )
            }
        }
    }
}

@Composable
private fun SidebarNavigation(
    currentScreen: Screen,
    onScreenSelected: (Screen) -> Unit,
    uiState: AppUiState
) {
    val screens = listOf(
        Screen.Generate,
        Screen.Models,
        Screen.Channels,
        Screen.Constraints,
        Screen.Gallery,
        Screen.Settings
    )

    Surface(
        modifier = Modifier
            .width(72.dp)
            .fillMaxHeight(),
        color = SurfaceDark
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top section - Logo (使用 app 图标)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(PrimaryIndigo),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = "SD-AI",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // Middle section - Navigation items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                screens.forEach { screen ->
                    NavItem(
                        screen = screen,
                        isSelected = currentScreen == screen,
                        onClick = { onScreenSelected(screen) }
                    )
                }
            }

            // Bottom section - 本地硬件监控 + 连接状态
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp),
                horizontalAlignment = Alignment.Start,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // 简洁水平硬件监控条：4 行 [标签 | 数字 | 状态]
                val ramPct = if (uiState.localRamTotalGb > 0)
                    (uiState.localRamUsedGb / uiState.localRamTotalGb * 100).coerceIn(0f, 100f)
                else -1f

                HwBar(
                    label = "CPU",
                    pct = uiState.localCpuUsage,
                    detail = null,
                    color = when {
                        uiState.localCpuUsage < 0 -> TextTertiary
                        uiState.localCpuUsage > 80 -> WarningYellow
                        uiState.localCpuUsage > 50 -> PrimaryIndigo
                        else -> SuccessGreen
                    }
                )
                HwBar(
                    label = "RAM",
                    pct = ramPct,
                    detail = if (uiState.localRamTotalGb > 0)
                        "${uiState.localRamUsedGb.toInt()}/${uiState.localRamTotalGb.toInt()}G"
                    else null,
                    color = when {
                        ramPct < 0 -> TextTertiary
                        ramPct > 90 -> WarningYellow
                        ramPct > 70 -> PrimaryIndigo
                        else -> SuccessGreen
                    }
                )
                HwBar(
                    label = "GPU",
                    pct = uiState.localGpuUsage,
                    detail = when {
                        uiState.localGpuUsage >= 0f && uiState.localGpuFreqMhz > 0 ->
                            "${uiState.localGpuUsage.toInt()}% ${uiState.localGpuFreqMhz}MHz"
                        uiState.localGpuUsage >= 0f ->
                            "${uiState.localGpuUsage.toInt()}%"
                        uiState.localNativeHeapMb >= 500 -> "推理中·${uiState.localNativeHeapMb}M"
                        uiState.localNativeHeapMb > 0 -> "空闲·${uiState.localNativeHeapMb}M"
                        else -> null
                    },
                    color = when {
                        uiState.localGpuUsage < 0 && uiState.localNativeHeapMb < 500 -> TextTertiary
                        uiState.localNativeHeapMb >= 500 -> PrimaryIndigo
                        else -> SuccessGreen
                    }
                )
                HwBar(
                    label = "TEMP",
                    pct = -1f,  // 温度不用进度条
                    detail = if (uiState.localCpuTemp >= 0)
                        "${uiState.localCpuTemp.toInt()}°C"
                    else "N/A",
                    color = when {
                        uiState.localCpuTemp < 0 -> TextTertiary
                        uiState.localCpuTemp > 60 -> WarningYellow
                        uiState.localCpuTemp > 45 -> PrimaryIndigo
                        else -> SuccessGreen
                    }
                )
            }
        }
    }
}

/**
 * 简洁水平硬件监控条
 * 单行：左侧标签 | 中间细长进度条 | 右侧数值
 * pct < 0 表示无数据（不画进度条，只显示灰色文字）
 */
@Composable
private fun HwBar(
    label: String,
    pct: Float,
    detail: String?,
    color: Color
) {
    val hasData = pct >= 0f
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp)
    ) {
        // 顶部：标签 + 数值
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                text = when {
                    hasData && detail != null -> "${pct.toInt()}% $detail"
                    hasData -> "${pct.toInt()}%"
                    detail != null -> detail
                    else -> "—"
                },
                color = if (hasData) color else TextTertiary,
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
        // 底部：进度条（无数据时画灰色细线占位）
        LinearProgressIndicator(
            progress = { if (hasData) (pct / 100f).coerceIn(0f, 1f) else 0f },
            modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = if (hasData) color else BorderDark,
            trackColor = BorderDark.copy(alpha = 0.4f),
            strokeCap = StrokeCap.Round
        )
    }
}

@Composable
private fun NavItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        PrimaryIndigo.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    val iconColor = if (isSelected) {
        PrimaryIndigo
    } else {
        TextTertiary
    }

    val textColor = if (isSelected) {
        PrimaryIndigo
    } else {
        TextTertiary
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = screen.icon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )

            Text(
                text = screen.title,
                color = textColor,
                fontSize = 11.sp,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 1
            )
        }
    }
}
