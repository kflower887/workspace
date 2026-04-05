package com.foldablebrowser.app.browser

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/** 히스토리 항목 */
data class HistoryItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis()
)

/** 북마크 항목 */
data class BookmarkItem(
    val id: Long = System.currentTimeMillis(),
    val title: String,
    val url: String
)

/** 브라우저 설정 */
data class BrowserSettings(
    val homePage: String = "https://www.google.com",
    val textSize: Int = 100,        // WebView textZoom (%)
    val jsEnabled: Boolean = true,
    val desktopMode: Boolean = false
)

/**
 * 브라우저 상태 ViewModel
 */
class BrowserViewModel : ViewModel() {

    val tabs = MutableLiveData<MutableList<TabItem>>(mutableListOf())
    val activeTabIndex = MutableLiveData(0)
    val foldableMode = MutableLiveData(FoldableMode.DUAL)
    val isLoading = MutableLiveData(false)
    val currentUrl = MutableLiveData("")
    val pageTitle = MutableLiveData("새 탭")
    val canGoBack = MutableLiveData(false)
    val canGoForward = MutableLiveData(false)
    val loadProgress = MutableLiveData(0)

    /** 동시 스크롤 ON/OFF */
    val syncScrollEnabled = MutableLiveData(true)

    /** 화면 강제 가로 모드 */
    val forceLandscape = MutableLiveData(false)

    /** 히스토리 (최대 200개, 최신순) */
    val historyList = MutableLiveData<MutableList<HistoryItem>>(mutableListOf())

    /** 북마크 */
    val bookmarkList = MutableLiveData<MutableList<BookmarkItem>>(mutableListOf())

    /** 설정 */
    val settings = MutableLiveData(BrowserSettings())

    // ── 탭 관리 ──────────────────────────────────────────────────

    fun getActiveTab(): TabItem? {
        val list = tabs.value ?: return null
        val idx = activeTabIndex.value ?: return null
        return if (idx in list.indices) list[idx] else null
    }

    fun addTab(url: String = ""): Int {
        val list = tabs.value ?: mutableListOf()
        val newTab = TabItem(url = url, isActive = true)
        list.forEach { it.isActive = false }
        list.add(newTab)
        tabs.value = list
        val newIdx = list.size - 1
        activeTabIndex.value = newIdx
        return newIdx
    }

    fun closeTab(index: Int) {
        val list = tabs.value ?: return
        if (list.isEmpty()) return
        list.removeAt(index)
        if (list.isEmpty()) { addTab(); return }
        val newIdx = (index - 1).coerceAtLeast(0)
        list.forEach { it.isActive = false }
        list.getOrNull(newIdx)?.isActive = true
        activeTabIndex.value = newIdx
        tabs.value = list
    }

    fun switchTab(index: Int) {
        val list = tabs.value ?: return
        if (index !in list.indices) return
        list.forEach { it.isActive = false }
        list[index].isActive = true
        activeTabIndex.value = index
        tabs.value = list
    }

    fun switchMode(mode: FoldableMode) { foldableMode.value = mode }
    fun getTabCount(): Int = tabs.value?.size ?: 0

    // ── 히스토리 ──────────────────────────────────────────────────

    fun addHistory(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:")) return
        val list = historyList.value ?: mutableListOf()
        // 중복 제거 (같은 URL이 있으면 기존 삭제 후 최신으로 추가)
        list.removeAll { it.url == url }
        list.add(0, HistoryItem(title = title.ifBlank { url }, url = url))
        if (list.size > 200) list.subList(200, list.size).clear()
        historyList.value = list
    }

    fun clearHistory() { historyList.value = mutableListOf() }

    fun removeHistory(id: Long) {
        val list = historyList.value ?: return
        list.removeAll { it.id == id }
        historyList.value = list
    }

    // ── 북마크 ──────────────────────────────────────────────────

    fun addBookmark(title: String, url: String) {
        if (url.isBlank()) return
        val list = bookmarkList.value ?: mutableListOf()
        if (list.any { it.url == url }) return   // 중복 방지
        list.add(BookmarkItem(title = title.ifBlank { url }, url = url))
        bookmarkList.value = list
    }

    fun removeBookmark(id: Long) {
        val list = bookmarkList.value ?: return
        list.removeAll { it.id == id }
        bookmarkList.value = list
    }

    fun isBookmarked(url: String): Boolean =
        bookmarkList.value?.any { it.url == url } ?: false

    // ── 설정 ──────────────────────────────────────────────────

    fun updateSettings(newSettings: BrowserSettings) {
        settings.value = newSettings
    }
}
