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
 */
class WebtoonWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    /** 컷 분류 결과 콜백 (url, "wide"|"narrow", w, h) */
    var onCutClassified: ((src: String, type: String, w: Int, h: Int) -> Unit)? = null

    /** 레이아웃 적용 완료 콜백 */
    var onLayoutApplied: ((wideCuts: Int, narrowCuts: Int) -> Unit)? = null

    private var wideCutCount = 0
    private var narrowCutCount = 0
    private var currentPlatform = WebtoonPlatform.GENERIC

    /** JS → Kotlin 브리지 */
    inner class WebtoonBridge {
        @JavascriptInterface
        fun onCutClassified(src: String, type: String, w: Int, h: Int) {
            when (type) {
                "wide"   -> wideCutCount++
                "narrow" -> narrowCutCount++
            }
            onCutClassified?.invoke(src, type, w, h)
            Log.d("Webtoon", "Cut[$type] ${w}x${h} — $src")
        }

        @JavascriptInterface
        fun log(msg: String) {
            Log.d("WebtoonJS", msg)
        }
    }

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
        }
        addJavascriptInterface(WebtoonBridge(), "WebtoonBridge")
    }

    /**
     * 웹툰 레이아웃 JS 주입.
     * @param platform  플랫폼 (null 이면 현재 URL로 자동 감지)
     */
    fun injectWebtoonLayout(platform: WebtoonPlatform? = null) {
        val screenW = resources.displayMetrics.widthPixels
        currentPlatform = platform ?: WebtoonLayoutEngine.detectPlatform(url ?: "")
        wideCutCount = 0
        narrowCutCount = 0

        val script = WebtoonLayoutEngine.buildScript(currentPlatform, screenW)
        evaluateJavascript(script) { result ->
            Log.d("Webtoon", "Layout script result: $result")
            post {
                onLayoutApplied?.invoke(wideCutCount, narrowCutCount)
            }
        }
    }

    /** 웹툰 레이아웃 제거 → 원래 페이지로 복원 */
    fun resetWebtoonLayout() {
        evaluateJavascript(WebtoonLayoutEngine.buildResetScript()) { result ->
            Log.d("Webtoon", "Reset result: $result")
        }
        wideCutCount = 0
        narrowCutCount = 0
    }

    fun getCurrentPlatform(): WebtoonPlatform = currentPlatform
    fun getWideCutCount(): Int = wideCutCount
    fun getNarrowCutCount(): Int = narrowCutCount
}
