// ui/i18n/RememberText.kt
// 帮 Compose 页面快速拿到当前语言的文字
package com.sdaiapp.ui.i18n

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.sdaiapp.ui.viewmodel.AppUiState

@Composable
fun rememberText(state: AppUiState): AppText {
    return remember(state.language) { AppText.of(state.language) }
}
