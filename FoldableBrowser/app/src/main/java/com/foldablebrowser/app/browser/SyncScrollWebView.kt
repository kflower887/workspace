package com.foldablebrowser.app.browser

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView

/**
 * 동기화 스크롤 WebView
 * 폴더블 분할 화면에서 패널 간 스크롤 연동을 지원하는 커스텀 WebView
 *
 * 핵심 동작:
 * - 사용자가 이 패널에서 스크롤하면 연결된 다른 패널들도 offset 계산하여 동기화
 * - pageHeight: 각 패널이 담당하는 가상 페이지 높이 (전체 콘텐츠 / 패널 수)
 * - panelIndex: 이 WebView가 몇 번째 패널인지 (0-based)
 */
class SyncScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var panelIndex: Int = 0
    var totalPanels: Int = 1
    var onScrollChangedListener: ((scrollY: Int, panelIndex: Int) -> Unit)? = null

    private var isSyncScrolling = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isSyncScrolling) {
            onScrollChangedListener?.invoke(t, panelIndex)
        }
    }

    /**
     * 외부에서 스크롤 위치를 동기화할 때 호출
     * @param masterScrollY 마스터 패널(패널 0)의 실제 scrollY
     */
    fun syncScrollFromMaster(masterScrollY: Int) {
        if (totalPanels <= 1) return
        val contentH = computeVerticalScrollRange()
        val viewH = height
        if (contentH <= viewH) return

        // 전체 스크롤 가능 범위를 패널 수로 나누어 각 패널의 담당 구간 계산
        val totalScrollable = contentH - viewH
        val perPanel = totalScrollable.toFloat() / totalPanels

        // 이 패널의 scrollY = masterScrollY에서 이 패널 offset 빼기
        val targetScroll = (masterScrollY - panelIndex * perPanel).toInt()
            .coerceIn(0, totalScrollable)

        isSyncScrolling = true
        scrollTo(0, targetScroll)
        isSyncScrolling = false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return super.onTouchEvent(event)
    }
}
