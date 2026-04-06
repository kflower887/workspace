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
 * 폴더블 분할 브라우저 컨트롤러 v4
 *
 * ── 연동 OFF (기본) ─────────────────────────────────────────────────
 *   - 모든 패널 독립 스크롤. 사용자가 각 패널을 원하는 위치에 직접 놓는다.
 *   - 새 URL 로드 시 모든 패널이 맨 위(scrollY=0)에서 시작.
 *
 * ── 연동 ON ─────────────────────────────────────────────────────────
 *   - lockSyncFromCurrentPositions() 호출 시점의 각 슬레이브 scrollY 와
 *     마스터 scrollY 의 차이(offset)를 확정(lock).
 *   - 이후 마스터 스크롤 이벤트마다
 *       slaveScrollY = masterScrollY + lockedOffset
 *     로 슬레이브를 이동. 범위 클램핑 포함.
 *
 * ── 항상 좌/우 배치 ─────────────────────────────────────────────────
 *   패널은 항상 LinearLayout.HORIZONTAL 로 배치 (호출 측 MainActivity 에서 처리).
 */
class FoldableBrowserController(private val context: Context) {

    private val webViews = mutableListOf<SyncScrollWebView>()
    private var currentMode = FoldableMode.DUAL

    /** 현재 연동 상태 */
    var isSyncActive: Boolean = false
        private set

    var onPageStarted: ((url: String) -> Unit)? = null
    var onPageFinished: ((url: String) -> Unit)? = null
    var onTitleReceived: ((title: String) -> Unit)? = null
    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onReceivedIcon: ((icon: Bitmap?) -> Unit)? = null

    // ──────────────────────────────────────────────────────────────
    // 연동 ON/OFF
    // ──────────────────────────────────────────────────────────────

    /**
     * 연동 ON — 현재 각 패널 위치를 기준으로 오프셋을 확정한다.
     * @return 확정된 오프셋 리스트 (인덱스 0 = 마스터, 항상 0)
     */
    fun lockSyncFromCurrentPositions(): List<Int> {
        val master = webViews.firstOrNull() ?: return emptyList()
        val masterY = master.scrollY
        val offsets = mutableListOf<Int>()
        webViews.forEach { wv ->
            val offset = if (wv.panelIndex == 0) 0 else wv.scrollY - masterY
            wv.lockedOffsetFromMaster = if (wv.panelIndex == 0) null else offset
            offsets.add(offset)
        }
        isSyncActive = true
        return offsets
    }

    /**
     * 연동 OFF — 슬레이브 오프셋 해제, 모든 패널 독립 스크롤로 복귀.
     */
    fun unlockSync() {
        webViews.forEach { it.lockedOffsetFromMaster = null }
        isSyncActive = false
    }

    // ──────────────────────────────────────────────────────────────
    // WebView 생성
    // ──────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(panelIndex: Int): SyncScrollWebView {
        return SyncScrollWebView(context).apply {
            this.panelIndex = panelIndex
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                cacheMode = WebSettings.LOAD_DEFAULT
                databaseEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isScrollbarFadingEnabled = true

            // 마스터(패널0)만 스크롤 콜백 설치
            if (panelIndex == 0) {
                onScrollChangedListener = { masterScrollY ->
                    // 연동 중이면 슬레이브들을 갱신
                    if (isSyncActive) {
                        val masterMax = this.maxScrollY()
                        webViews.drop(1).forEach { slave ->
                            slave.applyMasterScrollClamped(masterScrollY, masterMax)
                        }
                    }
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onPageStarted?.invoke(url)
                    }
                }

                override fun onPageFinished(view: WebView, url: String) {
                    if (panelIndex == 0) {
                        this@FoldableBrowserController.onPageFinished?.invoke(url)
                        // 슬레이브에 같은 URL 로드
                        syncAllWebViewUrls(url)
                    } else {
                        // 슬레이브 로딩 완료: 연동 중이면 현재 마스터 위치에 맞춰 재갱신
                        if (isSyncActive) {
                            val masterWv = webViews.firstOrNull()
                            val masterY = masterWv?.scrollY ?: 0
                            val masterMax = masterWv?.maxScrollY() ?: 0
                            view.postDelayed({
                                applyMasterScrollClamped(masterY, masterMax)
                            }, 300)
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean = panelIndex != 0   // 슬레이브는 직접 네비게이션 차단
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    if (panelIndex == 0)
                        this@FoldableBrowserController.onProgressChanged?.invoke(newProgress)
                }

                override fun onReceivedTitle(view: WebView, title: String) {
                    if (panelIndex == 0)
                        this@FoldableBrowserController.onTitleReceived?.invoke(title)
                }

                override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
                    if (panelIndex == 0)
                        this@FoldableBrowserController.onReceivedIcon?.invoke(icon)
                }
            }
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 패널 관리
    // ──────────────────────────────────────────────────────────────

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
        isSyncActive = false   // 모드 전환 시 연동 해제

        repeat(count) { i -> webViews.add(createWebView(i)) }

        if (currentUrl.isNotEmpty()) loadUrl(currentUrl)
        return webViews.toList()
    }

    fun getPanels(): List<SyncScrollWebView> = webViews.toList()

    // ──────────────────────────────────────────────────────────────
    // 브라우저 조작
    // ──────────────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        // 새 URL 로드 시 연동 해제 → 사용자가 다시 위치를 정하고 잠근다
        unlockSync()
        webViews.firstOrNull()?.loadUrl(url)
    }

    fun goBack(): Boolean {
        val m = webViews.firstOrNull() ?: return false
        return if (m.canGoBack()) { m.goBack(); true } else false
    }

    fun goForward(): Boolean {
        val m = webViews.firstOrNull() ?: return false
        return if (m.canGoForward()) { m.goForward(); true } else false
    }

    fun canGoBack() = webViews.firstOrNull()?.canGoBack() ?: false
    fun canGoForward() = webViews.firstOrNull()?.canGoForward() ?: false
    fun getCurrentUrl() = webViews.firstOrNull()?.url ?: ""
    fun getTitle() = webViews.firstOrNull()?.title ?: ""

    fun reload() {
        unlockSync()
        webViews.firstOrNull()?.reload()
    }

    fun stopLoading() { webViews.forEach { it.stopLoading() } }

    fun destroy() {
        webViews.forEach { it.destroy() }
        webViews.clear()
    }

    // ──────────────────────────────────────────────────────────────
    // 내부 유틸
    // ──────────────────────────────────────────────────────────────

    private fun syncAllWebViewUrls(url: String) {
        webViews.drop(1).forEach { if (it.url != url) it.loadUrl(url) }
    }

}
