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
 * 폴더블 분할 브라우저 컨트롤러
 *
 * 핵심 개념:
 * - 모든 패널은 동일한 URL을 로드한다
 * - 패널 0이 마스터: 사용자가 스크롤하면 masterScrollY가 업데이트됨
 * - 패널 1, 2는 슬레이브: masterScrollY 기반으로 자신의 offset 계산하여 동기화
 *
 * 스크롤 계산 (패널 i, 전체 N개):
 *   totalScrollable = contentHeight - viewHeight
 *   perPanel = totalScrollable / N
 *   panel[i].scrollY = masterScrollY - i * perPanel  (clamp to [0, totalScrollable])
 *
 * 마스터 scrollY 범위: [0, totalScrollable]
 * - masterScrollY = 0 → 패널0 최상단, 패널1은 perPanel, 패널2는 2*perPanel
 * - masterScrollY = totalScrollable → 패널0 하단 부분, 마지막 패널 최하단
 */
class FoldableBrowserController(private val context: Context) {

    private val webViews = mutableListOf<SyncScrollWebView>()
    private var currentMode = FoldableMode.DUAL
    private var masterScrollY = 0
    private var isSyncing = false

    var onPageStarted: ((url: String) -> Unit)? = null
    var onPageFinished: ((url: String) -> Unit)? = null
    var onTitleReceived: ((title: String) -> Unit)? = null
    var onFaviconReceived: ((favicon: Bitmap?) -> Unit)? = null
    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onReceivedIcon: ((icon: Bitmap?) -> Unit)? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(panelIndex: Int, totalPanels: Int): SyncScrollWebView {
        return SyncScrollWebView(context).apply {
            this.panelIndex = panelIndex
            this.totalPanels = totalPanels
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(false)  // 패널 분할 브라우저에서 줌 비활성화
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
                        syncAllSlavePanels()
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
                        // 페이지 로딩 완료 후 슬레이브도 같은 URL 로드
                        syncAllWebViewUrls(url)
                        // 초기 스크롤 위치 설정
                        view.postDelayed({
                            masterScrollY = 0
                            syncAllSlavePanels()
                        }, 300)
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest
                ): Boolean {
                    // 패널 0만 URL 결정권, 나머지는 패널 0 따라가기
                    if (panelIndex == 0) {
                        return false
                    }
                    // 슬레이브 패널은 자체 네비게이션 막기
                    return true
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

    /**
     * 모드에 맞게 WebView 패널들을 생성/재생성
     */
    fun setupPanels(mode: FoldableMode): List<SyncScrollWebView> {
        currentMode = mode
        val count = when (mode) {
            FoldableMode.SINGLE -> 1
            FoldableMode.DUAL -> 2
            FoldableMode.TRIPLE -> 3
        }

        val currentUrl = webViews.firstOrNull()?.url ?: ""
        webViews.clear()

        repeat(count) { i ->
            webViews.add(createWebView(i, count))
        }

        if (currentUrl.isNotEmpty()) {
            loadUrl(currentUrl)
        }

        return webViews.toList()
    }

    /**
     * 모든 패널에 동일 URL 로드 (마스터만 실제 로드, 슬레이브는 onPageFinished에서 동기화)
     */
    fun loadUrl(url: String) {
        webViews.firstOrNull()?.loadUrl(url)
    }

    fun goBack(): Boolean {
        val master = webViews.firstOrNull() ?: return false
        return if (master.canGoBack()) {
            master.goBack()
            true
        } else false
    }

    fun goForward(): Boolean {
        val master = webViews.firstOrNull() ?: return false
        return if (master.canGoForward()) {
            master.goForward()
            true
        } else false
    }

    fun canGoBack() = webViews.firstOrNull()?.canGoBack() ?: false
    fun canGoForward() = webViews.firstOrNull()?.canGoForward() ?: false

    fun getCurrentUrl() = webViews.firstOrNull()?.url ?: ""
    fun getTitle() = webViews.firstOrNull()?.title ?: ""

    fun reload() {
        webViews.firstOrNull()?.reload()
    }

    fun destroy() {
        webViews.forEach { it.destroy() }
        webViews.clear()
    }

    fun stopLoading() {
        webViews.forEach { it.stopLoading() }
    }

    /** 현재 패널 목록 반환 (회전 시 재배치에 사용, WebView 재생성 없음) */
    fun getPanels(): List<SyncScrollWebView> = webViews.toList()

    /**
     * 슬레이브 패널 URL 동기화 (마스터와 동일 URL 로드)
     */
    private fun syncAllWebViewUrls(url: String) {
        webViews.drop(1).forEach { wv ->
            if (wv.url != url) {
                wv.loadUrl(url)
            }
        }
    }

    /**
     * 마스터 scrollY 기반으로 슬레이브 패널 스크롤 동기화
     */
    private fun syncAllSlavePanels() {
        if (webViews.size <= 1) return
        isSyncing = true
        webViews.drop(1).forEach { wv ->
            wv.syncScrollFromMaster(masterScrollY)
        }
        isSyncing = false
    }

    /**
     * 슬레이브 패널들의 로딩이 완료되면 초기 위치 설정
     */
    fun onSlavePageFinished(panelIdx: Int) {
        if (panelIdx > 0) {
            webViews.getOrNull(panelIdx)?.postDelayed({
                webViews.getOrNull(panelIdx)?.syncScrollFromMaster(masterScrollY)
            }, 200)
        }
    }
}
