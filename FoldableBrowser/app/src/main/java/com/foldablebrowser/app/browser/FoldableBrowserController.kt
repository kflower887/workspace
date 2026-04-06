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
 * 폴더블 분할 브라우저 컨트롤러 v6
 *
 * ── 일반 모드 ────────────────────────────────────────────────────────
 *   SINGLE / DUAL / TRIPLE 분할. 연동 OFF면 각 패널 독립 스크롤.
 *   연동 ON(lockSyncFromCurrentPositions) → 오프셋 고정 후 마스터 추종.
 *
 * ── 웹툰 모드 (버튼 네비게이션) ──────────────────────────────────────
 *   DUAL 분할 + [이전]/[다음] 버튼으로 좌/우 동시 페이지 이동.
 *
 *   연동 OFF 상태:
 *     버튼 클릭 → 현재 좌측 scrollY 기준 ±panelH 이동
 *     좌=new leftY, 우=leftY + panelH
 *
 *   연동 ON 상태 (lockWebtoonSync 호출 후):
 *     버튼 클릭 → 좌측만 ±panelH 이동, 우측은 offset(=panelH)으로 자동 추종
 *     스크롤도 좌측 드래그 시 우측이 panelH 뒤에서 자동 따라옴
 *
 *   ※ initWebtoonPageMode()는 언제나 연동 해제 후 좌=0, 우=panelH 초기 배치.
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
    // 웹툰 페이지 버튼 네비게이션
    // ──────────────────────────────────────────────────────────────

    /**
     * 웹툰 버튼 모드 초기화.
     * - 연동 완전 해제
     * - 좌=scrollY(0), 우=scrollY(panelH) 로 초기 배치
     */
    fun initWebtoonPageMode() {
        webViews.forEach { it.lockedOffsetFromMaster = null }
        isSyncActive = false
        isWebtoonSyncMode = false

        val left  = webViews.getOrNull(0) ?: return
        val right = webViews.getOrNull(1) ?: return
        val panelH = left.height
        if (panelH == 0) { left.post { initWebtoonPageMode() }; return }

        left.scrollTo(0, 0)
        right.post { right.scrollTo(0, panelH) }
    }

    /**
     * 웹툰 연동 스크롤 활성화.
     * 현재 좌측 scrollY 를 기준으로 우측 offset = panelH 고정.
     * 이후 좌측 드래그 → 우측 자동 추종.
     */
    fun lockWebtoonSync() {
        val left   = webViews.getOrNull(0) ?: return
        val right  = webViews.getOrNull(1) ?: return
        val panelH = left.height.takeIf { it > 0 } ?: return

        // 우측 offset = panelH (항상 좌측보다 한 화면 뒤)
        right.lockedOffsetFromMaster = panelH
        // 즉시 우측 위치도 맞춤
        right.scrollTo(0, (left.scrollY + panelH).coerceAtLeast(0))
        isSyncActive = true
        isWebtoonSyncMode = true
    }

    /**
     * 웹툰 연동 스크롤 해제.
     * 각 패널 독립 스크롤로 복귀 (현재 scrollY 유지).
     */
    fun unlockWebtoonSync() {
        webViews.forEach { it.lockedOffsetFromMaster = null }
        isSyncActive = false
        isWebtoonSyncMode = false
    }

    /**
     * 다음 페이지 쌍으로 이동 (좌/우 동시).
     *
     * ─ 동작 원리 ─────────────────────────────────────────────────
     * pageIndex 로 현재 페이지 쌍을 추적:
     *   index=0 → 좌=0*panelH(A),  우=1*panelH(B)
     *   index=1 → 좌=2*panelH(C),  우=3*panelH(D)
     *   index=2 → 좌=4*panelH(E),  우=5*panelH(F)
     *
     * 스크롤로 중간에 있어도 버튼을 누르면
     * 현재 좌측 scrollY를 panelH 단위로 snap→ 다음 짝수 index로 이동.
     * ─────────────────────────────────────────────────────────────
     */
    fun webtoonPageNext() {
        val left   = webViews.getOrNull(0) ?: return
        val right  = webViews.getOrNull(1) ?: return
        val panelH = left.height.takeIf { it > 0 } ?: return

        // 현재 좌측 위치를 panelH 단위로 snap한 뒤 +1 페이지쌍 (짝수 단위)
        val curIndex  = left.scrollY / panelH          // 현재 몇 번째 panelH 단위인지
        val nextIndex = (curIndex / 2 + 1) * 2         // 다음 짝수 index (2, 4, 6…)
        val newLeftY  = nextIndex * panelH

        applyBothPanels(left, right, newLeftY, panelH)
    }

    /**
     * 이전 페이지 쌍으로 이동 (좌/우 동시).
     */
    fun webtoonPagePrev() {
        val left   = webViews.getOrNull(0) ?: return
        val right  = webViews.getOrNull(1) ?: return
        val panelH = left.height.takeIf { it > 0 } ?: return

        // 현재 좌측 위치를 panelH 단위로 snap한 뒤 -1 페이지쌍 (짝수 단위)
        val curIndex  = left.scrollY / panelH
        // 현재 짝수 기준 index — 정확히 경계에 있으면 한 단계 더 내림
        val baseIndex = if (left.scrollY % panelH == 0 && curIndex % 2 == 0) curIndex
                        else (curIndex / 2) * 2
        val prevIndex = (baseIndex - 2).coerceAtLeast(0)
        val newLeftY  = prevIndex * panelH

        applyBothPanels(left, right, newLeftY, panelH)
    }

    /** 좌/우 패널을 newLeftY / newLeftY+panelH 로 이동 (연동 상태 반영) */
    private fun applyBothPanels(
        left: SyncScrollWebView, right: SyncScrollWebView,
        newLeftY: Int, panelH: Int
    ) {
        left.scrollTo(0, newLeftY)
        if (isSyncActive) {
            // 연동 ON: onScrollChanged 콜백이 우측 자동 이동
            // scrollTo 는 동기이므로 콜백이 즉시 호출됨 — 추가 처리 불필요
        } else {
            // 연동 OFF: 우측 직접 이동
            right.scrollTo(0, newLeftY + panelH)
        }
    }

    // ──────────────────────────────────────────────────────────────
    // 일반 모드 연동 (수동 위치 잠금)
    // ──────────────────────────────────────────────────────────────

    // enableWebtoonSync / disableWebtoonSync 는 lockWebtoonSync / unlockWebtoonSync 로 대체

    // ──────────────────────────────────────────────────────────────
    // 수동 연동 ON/OFF
    // ──────────────────────────────────────────────────────────────

    fun lockSyncFromCurrentPositions(): List<Int> {
        isWebtoonSyncMode = false
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
            // 터치 시 포커스 획득 → 소프트 키보드 정상 동작
            isFocusable = true
            isFocusableInTouchMode = true

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
                        // 웹툰 배치 재적용은 MainActivity의 onPageFinished 콜백에서 처리
                    } else {
                        // 슬레이브 로드 완료 → 연동 상태면 오프셋 재적용
                        if (isSyncActive) {
                            val master = webViews.firstOrNull()
                            val masterY   = master?.scrollY ?: 0
                            val masterMax = master?.maxScrollY() ?: 0
                            view.postDelayed({
                                if (isWebtoonSyncMode) {
                                    // 웹툰 연동 재적용 (현재 마스터 기준 offset=panelH)
                                    lockWebtoonSync()
                                } else {
                                    applyMasterScrollClamped(masterY, masterMax)
                                }
                            }, 200)
                        }
                    }
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView, request: WebResourceRequest
                ): Boolean {
                    if (panelIndex == 0) return false  // 마스터: 그냥 로드 허용
                    // ─────────────────────────────────────────────────────
                    // 슬레이브(우측 등)에서 링크 클릭
                    //  → 마스터(좌측)에 해당 URL 로드
                    //  → 마스터 onPageFinished 에서 syncAllWebViewUrls() 호출
                    //     → 슬레이브도 자동으로 같은 URL 로드됨
                    //  → 웹툰 모드면 로드 완료 후 initWebtoonPageMode() 재적용
                    // ─────────────────────────────────────────────────────
                    val url = request.url.toString()
                    val master = webViews.firstOrNull() ?: return true
                    // 웹툰 연동 해제 후 마스터 로드 (onPageFinished가 슬레이브 동기화 처리)
                    unlockSync()
                    master.loadUrl(url)
                    return true  // 슬레이브 자체 로드는 차단
                }
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

    // ──────────────────────────────────────────────────────────────
    // 하위 호환 래퍼 (MainActivity 기존 호출 유지)
    // ──────────────────────────────────────────────────────────────
    fun enableWebtoonSync()  = lockWebtoonSync()
    fun disableWebtoonSync() = unlockWebtoonSync()
}
