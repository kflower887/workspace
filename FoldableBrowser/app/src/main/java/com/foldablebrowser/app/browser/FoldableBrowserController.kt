package com.foldablebrowser.app.browser

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient

/**
 * 폴더블 분할 브라우저 컨트롤러 v2
 *
 * ■ 패널 배치: 항상 가로(좌/우) 분할 — 세로 회전 시에도 동일
 * ■ 동시 스크롤(syncEnabled=true):
 *     마스터(패널0) scrollY → 슬레이브는 각자 구간 오프셋으로 연동
 * ■ 독립 스크롤(syncEnabled=false):
 *     각 패널 독립 조작. 슬레이브 초기 위치 = 마스터 뷰 높이 * panelIndex
 *     → "좌측 화면 하단 다음"이 우측 화면 최상단에 오는 효과
 */
class FoldableBrowserController(private val context: Context) {

    private val webViews = mutableListOf<SyncScrollWebView>()
    private var currentMode = FoldableMode.DUAL
    private var masterScrollY = 0
    private var isSyncing = false

    /** 동시 스크롤 활성 여부 (외부에서 변경 가능) */
    var syncEnabled: Boolean = true
        set(value) {
            field = value
            webViews.forEach { it.syncEnabled = value }
            if (!value) applyInitialOffsets()
        }

    var onPageStarted: ((url: String) -> Unit)? = null
    var onPageFinished: ((url: String) -> Unit)? = null
    var onTitleReceived: ((title: String) -> Unit)? = null
    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onReceivedIcon: ((icon: Bitmap?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(panelIndex: Int, totalPanels: Int): SyncScrollWebView {
        return SyncScrollWebView(context).apply {
            this.panelIndex = panelIndex
            this.totalPanels = totalPanels
            this.syncEnabled = this@FoldableBrowserController.syncEnabled
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)
                builtInZoomControls = false
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isScrollbarFadingEnabled = true

            // 패널 0이 마스터 스크롤 소스
            if (panelIndex == 0) {
                onScrollChangedListener = { scrollY, _ ->
                    if (!isSyncing) {
                        masterScrollY = scrollY
                        if (syncEnabled) syncAllSlavePanels()
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (panelIndex == 0) {
                        masterScrollY = 0
                        this@FoldableBrowserController.onPageStarted?.invoke(url)
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onPageFinished?.invoke(url)
                        // 슬레이브에 같은 URL 로드
                        syncAllWebViewUrls(url)
                        // 초기 스크롤 위치 설정
                        view.postDelayed({
                            masterScrollY = 0
                            if (syncEnabled) {
                                syncAllSlavePanels()
                            } else {
                                applyInitialOffsets()
                            }
                        }, 400)
                    } else {
                        // 슬레이브 로딩 완료 후 위치 재설정
                        view.postDelayed({
                            if (syncEnabled) {
                                syncAllSlavePanels()
                            } else {
                                applyInitialOffsets()
                            }
                        }, 300)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 패널 0만 URL 결정권, 슬레이브는 패널 0 따라감
                    return panelIndex != 0
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onProgressChanged?.invoke(newProgress)
                    }
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onTitleReceived?.invoke(title)
                    }
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onReceivedIcon?.invoke(icon)
                    }
                }
            }
        }
    }

    /** 모드에 맞게 WebView 패널 생성 */
    fun setupPanels(mode: FoldableMode): List<SyncScrollWebView> {
        currentMode = mode
        val count = when (mode) {
            FoldableMode.SINGLE -> 1
            FoldableMode.DUAL -> 2
            FoldableMode.TRIPLE -> 3
        }

        val currentUrl = webViews.firstOrNull()?.url ?: ""
        webViews.forEach { it.destroy() }
        webViews.clear()

        repeat(count) { i ->
            webViews.add(createWebView(i, count))
        }

        if (currentUrl.isNotEmpty()) loadUrl(currentUrl)

        return webViews.toList()
    }

    /** 현재 패널 목록 반환 (회전 시 재배치용, WebView 재생성 없음) */
    fun getPanels(): List<SyncScrollWebView> = webViews.toList()

    fun loadUrl(url: String) {
        webViews.firstOrNull()?.loadUrl(url)
    }

    fun goBack(): Boolean {
        val master = webViews.firstOrNull() ?: return false
        return if (master.canGoBack()) { master.goBack(); true } else false
    }

    fun goForward(): Boolean {
        val master = webViews.firstOrNull() ?: return false
        return if (master.canGoForward()) { master.goForward(); true } else false
    }

    fun canGoBack() = webViews.firstOrNull()?.canGoBack() ?: false
    fun canGoForward() = webViews.firstOrNull()?.canGoForward() ?: false
    fun getCurrentUrl() = webViews.firstOrNull()?.url ?: ""
    fun getTitle() = webViews.firstOrNull()?.title ?: ""

    fun reload() { webViews.firstOrNull()?.reload() }
    fun stopLoading() { webViews.forEach { it.stopLoading() } }

    fun destroy() {
        webViews.forEach { it.destroy() }
        webViews.clear()
    }

    // ── 내부 유틸 ──────────────────────────────────────────────────

    private fun syncAllWebViewUrls(url: String) {
        webViews.drop(1).forEach { wv ->
            if (wv.url != url) wv.loadUrl(url)
        }
    }

    private fun syncAllSlavePanels() {
        if (webViews.size <= 1) return
        isSyncing = true
        webViews.drop(1).forEach { it.syncScrollFromMaster(masterScrollY) }
        isSyncing = false
    }

    /**
     * 동시스크롤 비활성 시: 슬레이브 패널들의 초기 위치를
     * "마스터 뷰 높이 × panelIndex" 로 설정
     */
    private fun applyInitialOffsets() {
        val master = webViews.firstOrNull() ?: return
        val masterViewH = master.height.takeIf { it > 0 } ?: return
        webViews.drop(1).forEach { wv ->
            wv.setInitialOffsetFromMasterHeight(masterViewH, wv.panelIndex)
        }
    }
}
