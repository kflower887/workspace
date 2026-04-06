package com.foldablebrowser.app.webtoon

/**
 * 웹툰 컷 비율 감지 & 2열 자동 레이아웃 JS/CSS 인젝터
 *
 * ── 동작 원리 ────────────────────────────────────────────────────────────────
 *  1. 플랫폼별 셀렉터로 웹툰 이미지(<img>) 목록을 수집한다.
 *  2. 각 이미지의 naturalWidth / naturalHeight 비율을 읽는다.
 *     - ratio > WIDE_THRESHOLD  → wide-cut  (전체 너비 100%)
 *     - ratio ≤ WIDE_THRESHOLD  → narrow-cut (50% → 2열 배치)
 *  3. min-width 임계값(MIN_NARROW_PX)으로 말풍선이 너무 작아지는 것을 방지.
 *     → 화면 너비의 절반이 MIN_NARROW_PX 미만이면 narrow-cut을 wide로 격상.
 *  4. 이미지가 아직 로드 안 됐으면 onload 콜백으로 처리.
 *  5. MutationObserver로 동적으로 추가되는 컷도 감지한다 (무한스크롤 대응).
 *
 * ── 2열 배치 세부 ────────────────────────────────────────────────────────────
 *  - 컨테이너를 flexbox(flex-wrap: wrap)으로 변환
 *  - narrow-cut 두 개가 한 행을 채우고 나머지 공간은 없음
 *  - wide-cut은 flex-basis: 100%로 단독 행 차지
 *  - 컷 사이 gap: 2px (너무 붙으면 컷 경계 구분 어려움)
 *
 * ── 플랫폼 셀렉터 ───────────────────────────────────────────────────────────
 *  WebtoonPlatform enum에 정의. 범용(GENERIC) 규칙이 fallback으로 동작.
 */
object WebtoonLayoutEngine {

    /** 와이드컷 판정 비율 (가로/세로). 이 값 초과 → 전체 너비 */
    private const val WIDE_THRESHOLD = 1.2f

    /** narrow-cut 최소 표시 너비(px 기준). 이보다 좁으면 wide로 격상 */
    private const val MIN_NARROW_PX = 200

    // ─────────────────────────────────────────────────────────────────────────
    // 메인 JS 생성
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * WebView.evaluateJavascript() 에 그대로 넣을 수 있는 JS 문자열 반환.
     * @param platform  감지된(또는 사용자 선택) 플랫폼
     * @param screenWidthPx  디바이스 화면 너비 (px)
     */
    fun buildScript(platform: WebtoonPlatform, screenWidthPx: Int): String {
        val selectors = platform.imageSelectors.joinToString(", ") { "'$it'" }
        val containerSelectors = platform.containerSelectors.joinToString(", ") { "'$it'" }
        val minNarrow = MIN_NARROW_PX
        val wideThreshold = WIDE_THRESHOLD
        val halfWidth = screenWidthPx / 2

        return """
(function() {
  'use strict';

  /* ── 상수 ── */
  const WIDE_THRESHOLD  = $wideThreshold;
  const MIN_NARROW_PX   = $minNarrow;
  const HALF_WIDTH_PX   = $halfWidth;
  const IMG_SELECTORS   = [$selectors];
  const CONT_SELECTORS  = [$containerSelectors];

  /* ── CSS 주입 (한 번만) ── */
  if (!document.getElementById('__wt_style__')) {
    const style = document.createElement('style');
    style.id = '__wt_style__';
    style.textContent = `
      .__wt_container__ {
        display: flex !important;
        flex-wrap: wrap !important;
        gap: 2px !important;
        padding: 0 !important;
        margin: 0 auto !important;
        width: 100% !important;
        box-sizing: border-box !important;
        align-items: flex-start !important;
      }
      .__wt_wide__ {
        flex: 0 0 100% !important;
        width: 100% !important;
        max-width: 100% !important;
        height: auto !important;
        display: block !important;
        object-fit: contain !important;
      }
      .__wt_narrow__ {
        flex: 0 0 calc(50% - 1px) !important;
        width: calc(50% - 1px) !important;
        max-width: calc(50% - 1px) !important;
        height: auto !important;
        display: block !important;
        object-fit: contain !important;
      }
      /* 홀수 narrow가 마지막일 때 전체 너비로 확장 */
      .__wt_narrow__:last-child:nth-child(odd) {
        flex: 0 0 100% !important;
        width: 100% !important;
        max-width: 100% !important;
      }
    `;
    document.head.appendChild(style);
  }

  /* ── 이미지 분류 함수 ── */
  function classifyImage(img) {
    function apply(w, h) {
      img.classList.remove('__wt_wide__', '__wt_narrow__');
      const ratio = w / Math.max(h, 1);
      const tooNarrowPanel = (HALF_WIDTH_PX < MIN_NARROW_PX);
      if (ratio > WIDE_THRESHOLD || tooNarrowPanel) {
        img.classList.add('__wt_wide__');
        img.style.setProperty('width', '100%', 'important');
        img.style.setProperty('max-width', '100%', 'important');
      } else {
        img.classList.add('__wt_narrow__');
        img.style.setProperty('width', 'calc(50% - 1px)', 'important');
        img.style.setProperty('max-width', 'calc(50% - 1px)', 'important');
      }
      img.style.setProperty('height', 'auto', 'important');
      /* Android JS bridge 에 결과 전달 */
      if (window.WebtoonBridge) {
        window.WebtoonBridge.onCutClassified(
          img.src || '',
          ratio > WIDE_THRESHOLD ? 'wide' : 'narrow',
          w, h
        );
      }
    }

    if (img.complete && img.naturalWidth > 0) {
      apply(img.naturalWidth, img.naturalHeight);
    } else {
      img.addEventListener('load', function handler() {
        img.removeEventListener('load', handler);
        apply(img.naturalWidth || img.offsetWidth, img.naturalHeight || img.offsetHeight);
      });
      img.addEventListener('error', function() {
        img.classList.add('__wt_wide__');
      });
    }
  }

  /* ── 컨테이너 변환 ── */
  function transformContainer(container) {
    if (container.__wt_done__) return;
    container.__wt_done__ = true;
    container.classList.add('__wt_container__');
    /* 부모 요소의 width 제약 해제 */
    container.style.setProperty('max-width', '100%', 'important');
    container.style.setProperty('width', '100%', 'important');
    container.querySelectorAll('img').forEach(classifyImage);
  }

  /* ── 셀렉터 탐색 ── */
  function findAndTransform() {
    let found = false;

    /* 1) 플랫폼 전용 컨테이너 */
    CONT_SELECTORS.forEach(sel => {
      try {
        document.querySelectorAll(sel).forEach(el => {
          transformContainer(el);
          found = true;
        });
      } catch(e) {}
    });

    /* 2) 플랫폼 전용 이미지 (컨테이너 없이 직접) */
    if (!found) {
      IMG_SELECTORS.forEach(sel => {
        try {
          document.querySelectorAll(sel).forEach(img => {
            const parent = img.parentElement;
            if (parent && !parent.__wt_done__) transformContainer(parent);
            else classifyImage(img);
            found = true;
          });
        } catch(e) {}
      });
    }

    /* 3) Fallback: 화면 너비의 30% 이상인 이미지 모두 */
    if (!found) {
      document.querySelectorAll('img').forEach(img => {
        const w = img.offsetWidth || img.naturalWidth || 0;
        if (w > window.innerWidth * 0.3) {
          const parent = img.parentElement;
          if (parent && !parent.__wt_done__) transformContainer(parent);
          else classifyImage(img);
        }
      });
    }
  }

  /* ── MutationObserver: 무한스크롤 대응 ── */
  if (!window.__wt_observer__) {
    window.__wt_observer__ = new MutationObserver(function(mutations) {
      let needsUpdate = false;
      mutations.forEach(function(m) {
        m.addedNodes.forEach(function(node) {
          if (node.nodeType === 1) needsUpdate = true;
        });
      });
      if (needsUpdate) findAndTransform();
    });
    window.__wt_observer__.observe(document.body, {
      childList: true, subtree: true
    });
  }

  /* ── 즉시 실행 ── */
  findAndTransform();

  /* ── 지연 재실행 (이미지 지연 로딩 대응) ── */
  setTimeout(findAndTransform, 600);
  setTimeout(findAndTransform, 1500);
  setTimeout(findAndTransform, 3000);
  setTimeout(findAndTransform, 6000);

  return 'WebtoonLayout:OK';
})();
        """.trimIndent()
    }

    /**
     * 웹툰 레이아웃을 완전히 제거하고 원래 페이지로 복원하는 JS
     */
    fun buildResetScript(): String = """
(function() {
  const style = document.getElementById('__wt_style__');
  if (style) style.remove();
  document.querySelectorAll('.__wt_container__').forEach(el => {
    el.classList.remove('__wt_container__');
    el.__wt_done__ = false;
    el.style.removeProperty('max-width');
    el.style.removeProperty('width');
  });
  document.querySelectorAll('.__wt_wide__, .__wt_narrow__').forEach(img => {
    img.classList.remove('__wt_wide__', '__wt_narrow__');
    img.style.removeProperty('width');
    img.style.removeProperty('max-width');
    img.style.removeProperty('height');
  });
  if (window.__wt_observer__) {
    window.__wt_observer__.disconnect();
    window.__wt_observer__ = null;
  }
  return 'WebtoonLayout:RESET';
})();
    """.trimIndent()

    /**
     * 현재 페이지 URL 기반으로 플랫폼을 자동 감지한다.
     */
    fun detectPlatform(url: String): WebtoonPlatform {
        val lower = url.lowercase()
        return when {
            "comic.naver.com" in lower || "webtoon.naver.com" in lower -> WebtoonPlatform.NAVER
            "webtoon.kakao.com" in lower || "kakaopage.com" in lower   -> WebtoonPlatform.KAKAO
            "lezhin.com" in lower                                       -> WebtoonPlatform.LEZHIN
            "bomtoon.com" in lower                                      -> WebtoonPlatform.BOMTOON
            "toomics.com" in lower                                      -> WebtoonPlatform.TOOMICS
            "tapas.io" in lower                                         -> WebtoonPlatform.TAPAS
            "webtoons.com" in lower                                     -> WebtoonPlatform.LINE
            else                                                        -> WebtoonPlatform.GENERIC
        }
    }
}
