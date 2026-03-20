package com.foldablebrowser.app.browser

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

/**
 * 브라우저 상태 ViewModel
 * UI와 브라우저 로직을 분리하여 설정 변경 시에도 상태 유지
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
        if (list.isEmpty()) {
            addTab()
            return
        }
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

    fun switchMode(mode: FoldableMode) {
        foldableMode.value = mode
    }

    fun getTabCount(): Int = tabs.value?.size ?: 0
}
