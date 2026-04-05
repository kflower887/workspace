package com.foldablebrowser.app.browser

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * 동기화 스크롤 WebView
 *
 * ■ 동시 스크롤 활성(syncEnabled = true)
 *   - 패널 0(마스터)이 스크롤하면 패널 1·2는 각자 구간 오프셋으로 따라감
 *   - 공식: slaveScrollY = masterScrollY - panelIndex * (totalScrollable / totalPanels)
 *
 * ■ 동시 스크롤 비활성(syncEnabled = false)
 *   - 각 패널이 완전히 독립적으로 스크롤
 *   - 단, URL·내용은 동일 (마스터가 로딩 완료 후 슬레이브에 같은 URL 로드)
 *   - 슬레이브 패널은 초기 위치가 마스터 화면 높이(viewHeight)만큼 아래로 설정됨
 *     → "좌측 화면 하단 다음 부분"을 보여주는 효과
 */
class SyncScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var panelIndex: Int = 0
    var totalPanels: Int = 1

    /** true = 동시스크롤 연동 / false = 독립 스크롤 (초기 오프셋만 적용) */
    var syncEnabled: Boolean = true

    /** 마스터 패널의 스크롤 변화를 콜백으로 전달 */
    var onScrollChangedListener: ((scrollY: Int, panelIndex: Int) -> Unit)? = null

    private var isSyncScrolling = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        if (!isSyncScrolling) {
            onScrollChangedListener?.invoke(t, panelIndex)
        }
    }

    /**
     * 동시 스크롤 모드: 마스터 scrollY 기반으로 이 패널의 위치 계산
     */
    fun syncScrollFromMaster(masterScrollY: Int) {
        if (totalPanels <= 1 || !syncEnabled) return
        val contentH = computeVerticalScrollRange()
        val viewH = height
        if (contentH <= viewH) return

        val totalScrollable = contentH - viewH
        val perPanel = totalScrollable.toFloat() / totalPanels
        val targetScroll = (masterScrollY - panelIndex * perPanel).toInt()
            .coerceIn(0, totalScrollable)

        isSyncScrolling = true
        scrollTo(0, targetScroll)
        isSyncScrolling = false
    }

    /**
     * 비동시 스크롤 모드: 슬레이브 패널을 마스터 뷰 높이만큼 아래로 초기 배치
     * → "좌측(마스터) 화면 하단 바로 다음"이 우측(슬레이브) 최상단에 오는 효과
     */
    fun setInitialOffsetFromMasterHeight(masterViewHeight: Int, panelIdx: Int) {
        if (panelIdx == 0) return
        val contentH = computeVerticalScrollRange()
        val viewH = height
        if (contentH <= viewH) return

        val totalScrollable = contentH - viewH
        // 패널 N의 시작 위치 = masterViewHeight * N
        val targetScroll = (masterViewHeight * panelIdx).coerceIn(0, totalScrollable)

        isSyncScrolling = true
        scrollTo(0, targetScroll)
        isSyncScrolling = false
    }
}
