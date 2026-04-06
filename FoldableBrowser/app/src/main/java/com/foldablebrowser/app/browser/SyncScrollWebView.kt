package com.foldablebrowser.app.browser

import android.content.Context
import android.util.AttributeSet
import android.webkit.WebView

/**
 * 동기화 스크롤 WebView
 *
 * ── 연동 OFF (기본) ────────────────────────────────────────────────
 *   각 패널이 완전히 독립적으로 스크롤된다.
 *   사용자가 좌측을 A 위치, 우측을 B 위치에 놓은 뒤 연동을 켠다.
 *
 * ── 연동 ON ────────────────────────────────────────────────────────
 *   연동을 켜는 순간 각 슬레이브 패널의 [lockedOffsetFromMaster] 가 확정된다.
 *     lockedOffsetFromMaster = slaveScrollY - masterScrollY  (부호 포함)
 *
 *   이후 마스터가 delta 만큼 스크롤하면
 *     슬레이브 목표 scrollY = masterScrollY + lockedOffsetFromMaster
 *   범위는 [0, maxScroll] 로 클램핑.
 *
 *   이 방식의 장점:
 *   - WebView contentHeight 계산 타이밍과 무관하게 정확히 동작
 *   - 사용자가 원하는 위치에서 직접 잠그므로 항상 직관적
 */
class SyncScrollWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    var panelIndex: Int = 0

    /** 마스터(패널0) 스크롤 이벤트 콜백 — 컨트롤러가 세팅 */
    var onScrollChangedListener: ((scrollY: Int) -> Unit)? = null

    /**
     * 연동 ON 시 확정된 오프셋.
     * slave.scrollY = master.scrollY + lockedOffsetFromMaster
     * null 이면 아직 잠금 전(연동 OFF 상태)
     */
    var lockedOffsetFromMaster: Int? = null

    private var isSyncScrolling = false

    override fun onScrollChanged(l: Int, t: Int, oldl: Int, oldt: Int) {
        super.onScrollChanged(l, t, oldl, oldt)
        // 마스터 패널만 콜백 (슬레이브가 프로그래밍 방식으로 스크롤될 때는 무시)
        if (panelIndex == 0 && !isSyncScrolling) {
            onScrollChangedListener?.invoke(t)
        }
    }

    /**
     * 마스터의 현재 scrollY 를 받아서 이 패널의 위치를 갱신한다.
     * lockedOffsetFromMaster 가 null 이면(연동 OFF) 아무것도 안 한다.
     */
    fun applyMasterScroll(masterScrollY: Int) {
        val offset = lockedOffsetFromMaster ?: return      // 연동 OFF 면 무시
        val contentH = computeVerticalScrollRange()
        val viewH = height
        val maxScroll = (contentH - viewH).coerceAtLeast(0)
        val target = (masterScrollY + offset).coerceIn(0, maxScroll)
        isSyncScrolling = true
        scrollTo(0, target)
        isSyncScrolling = false
    }

    /**
     * 마스터가 스크롤 끝에 도달하면 슬레이브 패널의 최대 범위까지 스크롤
     * (웹툰처럼 연속 읽기에 유용)
     */
    fun applyMasterScrollClamped(masterScrollY: Int, masterMaxScroll: Int) {
        val offset = lockedOffsetFromMaster ?: return
        val contentH = computeVerticalScrollRange()
        val viewH = height
        val maxScroll = (contentH - viewH).coerceAtLeast(0)

        // 마스터 진행률(0.0~1.0)에 기반한 슬레이브 위치 계산
        val progress = if (masterMaxScroll > 0) masterScrollY.toFloat() / masterMaxScroll else 0f
        val targetByProgress = (progress * maxScroll).toInt()
        // 오프셋 기반 계산
        val targetByOffset = masterScrollY + offset

        // 두 계산 중 더 정확한 것 선택 (오프셋이 범위 내면 오프셋 우선)
        val target = if (targetByOffset in 0..maxScroll) targetByOffset else targetByProgress
        isSyncScrolling = true
        scrollTo(0, target.coerceIn(0, maxScroll))
        isSyncScrolling = false
    }

    /** 현재 스크롤 가능한 최대 Y 값 */
    fun maxScrollY(): Int =
        (computeVerticalScrollRange() - height).coerceAtLeast(0)
}
