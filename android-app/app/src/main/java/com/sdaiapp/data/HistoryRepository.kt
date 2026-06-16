// data/HistoryRepository.kt
// 历史记录仓库 — 用 DataStore 存 JSON，关掉 APP 重开也不丢
package com.sdaiapp.data

import android.content.Context
import com.sdaiapp.utils.AppLog as Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.sdaiapp.data.model.HistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "history")

class HistoryRepository(private val context: Context) {

    private val TAG = "HistoryRepo"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
    private val key = stringPreferencesKey("history_list")

    // 读取全部历史记录（响应式，数据变化自动通知 UI）
    val historyFlow: Flow<List<HistoryItem>> = context.dataStore.data.map { prefs ->
        val raw = prefs[key] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<HistoryItem>>(raw) }
            .onFailure { Log.e(TAG, "历史记录反序列化失败，清除损坏数据", it) }
            .getOrDefault(emptyList())
    }

    // 保存整个列表（新增/删除后调用）
    suspend fun saveList(items: List<HistoryItem>) {
        try {
            val encoded = json.encodeToString(items.take(100))
            context.dataStore.edit { prefs ->
                prefs[key] = encoded
            }
        } catch (e: Exception) {
            Log.e(TAG, "历史记录序列化失败，清空损坏数据", e)
            // 序列化失败时清空旧数据，防止反复崩溃
            context.dataStore.edit { it.remove(key) }
        }
    }

    // 加一条到最前面
    suspend fun addItem(item: HistoryItem) {
        val current = getCurrentList().toMutableList()
        current.add(0, item)
        saveList(current)
    }

    // 删一条
    suspend fun deleteItem(id: String) {
        val current = getCurrentList().filter { it.id != id }
        saveList(current)
    }

    // 清空
    suspend fun clearAll() {
        context.dataStore.edit { it.remove(key) }
    }

    // 一次性拿当前列表（非响应式）
    private suspend fun getCurrentList(): List<HistoryItem> {
        val raw = context.dataStore.data.first()[key] ?: return emptyList()
        return runCatching { json.decodeFromString<List<HistoryItem>>(raw) }
            .onFailure { Log.e(TAG, "历史记录读取失败", it) }
            .getOrDefault(emptyList())
    }
}