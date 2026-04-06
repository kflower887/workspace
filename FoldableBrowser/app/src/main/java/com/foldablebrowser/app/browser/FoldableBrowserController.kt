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
 * 폴더블 분할 브라우저 컨트롤러 v5
 *
 * ── 일반 모드 ────────────────────────────────────────────────────────
 *   SINGLE / DUAL / TRIPLE 분할. 연동 OFF면 각 패널 독립 스크롤.
 *   연동 ON(lockSyncFromCurrentPositions) → 오프셋 고정 후 마스터 추종.
 *
 * ── 웹툰 모드 ────────────────────────────────────────────────────────
 *   DUAL 분할 + 페이지 로드 완료 시 우측을 "좌측 패널 높이(px)"만큼
 *   자동으로 오프셋 고정 → 좌측 = 1페이지, 우측 = 2페이지가 바로 연결.
 *   이후 좌측 스크롤 → 우측 자동 추종 (offset = panelHeight 고정).
 *
 *   핵심: panelHeight 는 WebView.height (실제 뷰 높이, px) 를 사용.
 *   contentHeight가 아닌 뷰 높이를 쓰므로 페이지 로드 전에도 정확.
 */
class FoldableBrowserController(private val context: Context) {

    private val webViews = mutableListOf<SyncScrollWebView>()
    private var currentMode = FoldableMode.DUAL

    /** 현재 연동 상태 */
    var isSyncActive: Boolean = false
        private set

    /** 웹툰 자동 연동 모드 */
    var isWebtoonSyncMode: Boolean = false
        private set

    var onPageStarted: ((url: String) -> Unit)? = null
    var onPageFinished: ((url: String) -> Unit)? = null
    var onTitleReceived: ((title: String) -> Unit)? = null
    var onProgressChanged: ((progress: Int) -> Unit)? = null
    var onReceivedIcon: ((icon: Bitmap?) -> Unit)? = null

    // ──────────────────────────────────────────────────────────────
    // 웹툰 페이지 버튼 네비게이션 (연동 없이 패널별 독립 이동)
    // ──────────────────────────────────────────────────────────────

    /**
     * 웹툰 버튼 모드 초기화.
     * - 연동 완전 해제
     * - 좌측(0): scrollY = 0 (1페이지 상단)
     * - 우측(1): scrollY = panelHeight (2페이지 상단, 즉 좌측 바로 다음)
     * 이후 좌/우 각각 독립적으로 버튼으로 페이지를 넘긴다.
     */
    fun initWebtoonPageMode() {
        // 연동 해제 → 각 패널 독립
        webViews.forEach { it.lockedOffsetFromMaster = null }
        isSyncActive = false
        isWebtoonSyncMode = false

        val master = webViews.getOrNull(0) ?: return
        val slave  = webViews.getOrNull(1) ?: return

        val panelH = master.height
        if (panelH == 0) {
            master.post { initWebtoonPageMode() }
            return
        }

        // 좌측: 1페이지(맨 위)
        master.scrollTo(0, 0)
        // 우측: 2페이지(패널 높이만큼 아래)
        slave.post { slave.scrollTo(0, panelH) }
    }

    /**
     * 특정 패널을 pageStep 만큼 이동 (pageStep = 패널 높이 배수).
     * direction > 0 이면 다음, < 0 이면 이전.
     */
    fun panelPageStep(panelIndex: Int, direction: Int) {
        val wv = webViews.getOrNull(panelIndex) ?: return
        val panelH = wv.height.takeIf { it > 0 } ?: return
        val currentY = wv.scrollY
        val newY = (currentY + direction * panelH).coerceAtLeast(0)
        wv.scrollTo(0, newY)
    }

    /** 좌측 패널 다음 페이지 */
    fun leftPageNext()  = panelPageStep(0, +1)
    /** 좌측 패널 이전 페이지 */
    fun leftPagePrev()  = panelPageStep(0, -1)
    /** 우측 패널 다음 페이지 */
    fun rightPageNext() = panelPageStep(1, +1)
    /** 우측 패널 이전 페이지 */
    fun rightPagePrev() = panelPageStep(1, -1)

    // ──────────────────────────────────────────────────────────────
    // 웹툰 자동 연동 (레거시 - 연동 ON/OFF 방식)
    // ──────────────────────────────────────────────────────────────

    /**
     * 웹툰 모드 자동 연동 활성화.
     * 마스터 패널 높이(px) × panelIndex 를 각 슬레이브의 오프셋으로 즉시 고정.
     * 페이지 로드가 완전히 끝나지 않아도 뷰 높이는 이미 확정되어 있으므로 정확.
     */
    fun enableWebtoonSync() {
        isWebtoonSyncMode = true
        applyWebtoonOffsets()
    }

    fun disableWebtoonSync() {
        isWebtoonSyncMode = false
        unlockSync()
    }

    /**
     * 각 슬레이브에 panelHeight × panelIndex 오프셋을 적용하고 isSyncActive = true.
     * 슬레이브 WebView가 아직 레이아웃되지 않은 경우(height=0) → post로 재시도.
     */
    private fun applyWebtoonOffsets() {
        val master = webViews.firstOrNull() ?: return
        val panelH = master.height   // 뷰 높이 (px) — 항상 패널 높이와 동일

        if (panelH == 0) {
            // 레이아웃 전이면 다음 프레임에 재시도
            master.post { applyWebtoonOffsets() }
            return
        }

        webViews.forEach { wv ->
            if (wv.panelIndex == 0) {
                wv.lockedOffsetFromMaster = null
            } else {
                // 슬레이브 n번: masterScrollY + panelH * n 위치에 표시
                val offset = panelH * wv.panelIndex
                wv.lockedOffsetFromMaster = offset
                // 현재 마스터 위치에 맞게 즉시 이동
                wv.applyMasterScrollClamped(master.scrollY, master.maxScrollY())
            }
        }
        isSyncActive = true
    }

    // ──────────────────────────────────────────────────────────────
    // 수동 연동 ON/OFF
    // ──────────────────────────────────────────────────────────────

    fun lockSyncFromCurrentPositions(): List<Int> {
        isWebtoonSyncMode = false   // 수동 잠금 시 웹툰 자동모드 해제
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
                @Suppress("DEPRECATION")
                databaseEnabled = true
                allowFileAccess = true
                mediaPlaybackRequiresUserGesture = false
            }
            scrollBarStyle = WebView.SCROLLBARS_OUTSIDE_OVERLAY
            isScrollbarFadingEnabled = true

            if (panelIndex == 0) {
                onScrollChangedListener = { masterScrollY ->
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
                        syncAllWebViewUrls(url)
                    } else {
                        // 슬레이브 로드 완료 → 웹툰 모드면 오프셋 즉시 재적용
                        if (isSyncActive) {
                            val master = webViews.firstOrNull()
                            val masterY   = master?.scrollY ?: 0
                            val masterMax = master?.maxScrollY() ?: 0
                            view.postDelayed({
                                if (isWebtoonSyncMode) applyWebtoonOffsets()
                                else applyMasterScrollClamped(masterY, masterMax)
                            }, 200)
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean = panelIndex != 0
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
            FoldableMode.DUAL   -> 2
            FoldableMode.TRIPLE -> 3
        }
        val currentUrl = webViews.firstOrNull()?.url ?: ""
        webViews.forEach { it.destroy() }
        webViews.clear()
        isSyncActive = false
        isWebtoonSyncMode = false
        repeat(count) { i -> webViews.add(createWebView(i)) }
        if (currentUrl.isNotEmpty()) loadUrl(currentUrl)
        return webViews.toList()
    }

    fun getPanels(): List<SyncScrollWebView> = webViews.toList()
    fun getMasterView(): SyncScrollWebView? = webViews.firstOrNull()

    // ──────────────────────────────────────────────────────────────
    // 브라우저 조작
    // ──────────────────────────────────────────────────────────────

    fun loadUrl(url: String) {
        unlockSync()
        isWebtoonSyncMode = false
        webViews.firstOrNull()?.loadUrl(url)
    }

    fun loadUrlWebtoon(url: String) {
        // 웹툰 모드에서 URL 로드: 로드 후 자동 오프셋 재적용 (isWebtoonSyncMode 유지)
        val wasWebtoon = isWebtoonSyncMode
        unlockSync()
        isWebtoonSyncMode = wasWebtoon
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

    fun canGoBack()    = webViews.firstOrNull()?.canGoBack()    ?: false
    fun canGoForward() = webViews.firstOrNull()?.canGoForward() ?: false
    fun getCurrentUrl() = webViews.firstOrNull()?.url ?: ""
    fun getTitle()      = webViews.firstOrNull()?.title ?: ""

    fun reload() {
        val wasWebtoon = isWebtoonSyncMode
        unlockSync()
        isWebtoonSyncMode = wasWebtoon
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
