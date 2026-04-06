package com.foldablebrowser.app.webtoon

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * 웹툰 전용 WebView
 *
 * - JavascriptInterface(WebtoonBridge)로 JS → Kotlin 컷 분류 결과 수신
 * - 페이지 로드 완료 후 WebtoonLayoutEngine 스크립트 자동 주입
 * - 외부에서 injectWebtoonLayout() / resetWebtoonLayout() 호출 가능
 *
 * 핵심 개선:
 * - lazy-load 이미지는 IntersectionObserver로 뷰포트 진입 시 강제 로드 후 분류
 * - MutationObserver로 동적 DOM 변경(무한스크롤) 대응
 * - onCutClassified 콜백은 실시간으로 누적 카운트 증가
 * - onLayoutApplied 는 첫 분류 결과 확정 후 콜백 (1회), 이후 추가 컷은 누적만
 */
class WebtoonWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    /** 컷 분류 결과 콜백 (src, "wide"|"narrow", w, h) */
    var onCutClassified: ((src: String, type: String, w: Int, h: Int) -> Unit)? = null

    /** 레이아웃 적용 완료 콜백 — (wideCuts, narrowCuts) */
    var onLayoutApplied: ((wideCuts: Int, narrowCuts: Int) -> Unit)? = null

    var wideCutCount   = 0
        private set
    var narrowCutCount = 0
        private set
    private var currentPlatform = WebtoonPlatform.GENERIC
    private var callbackFired = false   // 첫 배치 결과 콜백 중복 방지

    // ── JS ↔ Kotlin 브리지 ──────────────────────────────────────────────────

    inner class WebtoonBridge {

        @JavascriptInterface
        fun onCutClassified(src: String, type: String, w: Int, h: Int) {
            when (type) {
                "wide"   -> wideCutCount++
                "narrow" -> narrowCutCount++
            }
            onCutClassified?.invoke(src, type, w, h)
            Log.d("Webtoon", "Cut[$type] ${w}x${h}")

            // 첫 3컷 이상 분류되면 즉시 콜백 (UI 배지 업데이트)
            if (!callbackFired && (wideCutCount + narrowCutCount) >= 3) {
                callbackFired = true
                post { onLayoutApplied?.invoke(wideCutCount, narrowCutCount) }
            }
        }

        @JavascriptInterface
        fun log(msg: String) = Log.d("WebtoonJS", msg)
    }

    // ── 초기화 ───────────────────────────────────────────────────────────────

    @SuppressLint("SetJavaScriptEnabled")
    fun initialize() {
        settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = true
            mediaPlaybackRequiresUserGesture = false
            // lazy-load 이미지들이 제대로 로드되도록 캐시 활성화
            @Suppress("DEPRECATION")
            databaseEnabled = true
        }
        addJavascriptInterface(WebtoonBridge(), "WebtoonBridge")
    }

    // ── 웹툰 레이아웃 주입 ───────────────────────────────────────────────────

    /**
     * 웹툰 2열 레이아웃 JS 주입.
     * @param platform  플랫폼 (null이면 현재 URL 자동 감지)
     */
    fun injectWebtoonLayout(platform: WebtoonPlatform? = null) {
        val screenW = resources.displayMetrics.widthPixels
        currentPlatform = platform ?: WebtoonLayoutEngine.detectPlatform(url ?: "")

        // 카운터 리셋
        wideCutCount   = 0
        narrowCutCount = 0
        callbackFired  = false

        val script = WebtoonLayoutEngine.buildScript(currentPlatform, screenW)
        evaluateJavascript(script) { result ->
            Log.d("Webtoon", "Layout inject result: $result")
            // 스크립트 주입 후 1초 뒤에 최종 집계 콜백 (초기 분류 완료 예상 시점)
            postDelayed({
                if (!callbackFired) {
                    callbackFired = true
                    post { onLayoutApplied?.invoke(wideCutCount, narrowCutCount) }
                }
            }, 1000)
        }
    }

    /** 웹툰 레이아웃 제거 → 원래 페이지로 복원 */
    fun resetWebtoonLayout() {
        evaluateJavascript(WebtoonLayoutEngine.buildResetScript()) { result ->
            Log.d("Webtoon", "Reset result: $result")
        }
        wideCutCount   = 0
        narrowCutCount = 0
        callbackFired  = false
    }

    fun getCurrentPlatform(): WebtoonPlatform = currentPlatform
}
