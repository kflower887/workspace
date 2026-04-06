package com.foldablebrowser.app.webtoon

/**
 * 웹툰 컷 비율 감지 & 2열 자동 레이아웃 JS/CSS 인젝터
 *
 * ── 핵심 문제 및 해결 ──────────────────────────────────────────────────────────
 *  문제: 첫 이미지는 보이는데 그 아래 이미지들이 우측 열에 안 나타남
 *  원인:
 *   1) lazy loading — img.src 가 실제로 없고 data-src/data-lazy-src 등에 있음
 *   2) __wt_done__ = true 로 컨테이너 잠금 후, 새로 추가된 img는 분류 안 됨
 *   3) 이미지가 아직 로드 안 된 상태에서 naturalWidth = 0 → wide 판정 또는 미처리
 *   4) 일부 사이트에서 img 없이 div 배경(background-image)으로 컷을 표시
 *
 *  해결:
 *   - __wt_done__ 제거, 대신 img별로 __wt_classified__ 플래그 사용
 *   - lazy src 속성 (data-src, data-lazy, data-original 등) 자동 감지 & 강제 로드
 *   - IntersectionObserver 로 뷰포트에 들어오는 img 실시간 분류
 *   - MutationObserver 도 유지 (무한스크롤 대응)
 *   - 분류 실패(naturalWidth=0)시 기본값 narrow 처리 후 load 이벤트로 재분류
 *
 * ── 2열 배치 세부 ────────────────────────────────────────────────────────────
 *  - 컨테이너를 flexbox(flex-wrap: wrap)으로 변환
 *  - narrow-cut(세로 긴 컷): flex 50% → 두 컷이 나란히 한 행
 *  - wide-cut(가로 긴 컷):  flex 100% → 단독 행 차지
 *  - 홀수 narrow가 마지막이면 100%로 자동 확장
 *  - gap: 2px
 */
object WebtoonLayoutEngine {

    private const val WIDE_THRESHOLD = 1.2f   // ratio(w/h) > 이 값 → 와이드컷
    private const val MIN_NARROW_PX  = 200    // 절반 너비 < 이 값 → 무조건 wide

    fun buildScript(platform: WebtoonPlatform, screenWidthPx: Int): String {
        val imgSels  = platform.imageSelectors.joinToString(", ") { "'$it'" }
        val contSels = platform.containerSelectors.joinToString(", ") { "'$it'" }
        val halfWidth = screenWidthPx / 2

        return """
(function() {
  'use strict';

  /* ── 상수 ── */
  const WIDE_THRESHOLD = $WIDE_THRESHOLD;
  const MIN_NARROW_PX  = $MIN_NARROW_PX;
  const HALF_W         = $halfWidth;
  const IMG_SELS       = [$imgSels];
  const CONT_SELS      = [$contSels];

  /* ── CSS 주입 (한 번만) ── */
  if (!document.getElementById('__wt_style__')) {
    const s = document.createElement('style');
    s.id = '__wt_style__';
    s.textContent = `
      .__wt_cont__ {
        display: flex !important;
        flex-wrap: wrap !important;
        gap: 2px !important;
        padding: 0 !important;
        margin: 0 auto !important;
        width: 100% !important;
        max-width: 100% !important;
        box-sizing: border-box !important;
        align-items: flex-start !important;
        overflow: visible !important;
      }
      .__wt_wide__ {
        flex: 0 0 100% !important;
        width: 100% !important;
        max-width: 100% !important;
        height: auto !important;
        display: block !important;
      }
      .__wt_narrow__ {
        flex: 0 0 calc(50% - 1px) !important;
        width: calc(50% - 1px) !important;
        max-width: calc(50% - 1px) !important;
        height: auto !important;
        display: block !important;
      }
      .__wt_narrow__:last-child:nth-child(odd) {
        flex: 0 0 100% !important;
        width: 100% !important;
        max-width: 100% !important;
      }
    `;
    document.head.appendChild(s);
  }

  /* ── lazy-src 속성 목록 ── */
  const LAZY_ATTRS = [
    'data-src','data-lazy-src','data-original','data-lazy',
    'data-hi-res-src','data-url','data-image','lazy-src',
    'data-actualsrc','data-srcset'
  ];

  /* ── lazy 이미지에 실제 src 강제 설정 ── */
  function forceLoadSrc(img) {
    if (img.__wt_src_forced__) return;
    for (const attr of LAZY_ATTRS) {
      const v = img.getAttribute(attr);
      if (v && v.startsWith('http') && img.src !== v) {
        img.src = v;
        img.__wt_src_forced__ = true;
        return;
      }
    }
    /* srcset 처리 */
    if (!img.src || img.src === window.location.href) {
      const ss = img.getAttribute('srcset') || img.getAttribute('data-srcset');
      if (ss) {
        const first = ss.split(',')[0].trim().split(/\s+/)[0];
        if (first) { img.src = first; img.__wt_src_forced__ = true; }
      }
    }
  }

  /* ── 이미지 분류 (wide / narrow) ── */
  function applyClass(img, w, h) {
    img.classList.remove('__wt_wide__', '__wt_narrow__');
    const ratio = w / Math.max(h, 1);
    const isWide = ratio > WIDE_THRESHOLD || HALF_W < MIN_NARROW_PX;
    const cls    = isWide ? '__wt_wide__' : '__wt_narrow__';
    img.classList.add(cls);
    img.__wt_classified__ = true;
    if (window.WebtoonBridge) {
      try {
        WebtoonBridge.onCutClassified(img.src || '', isWide ? 'wide' : 'narrow', w, h);
      } catch(e) {}
    }
  }

  function classifyImg(img) {
    /* 이미 분류된 경우 재분류하지 않음 (단, naturalWidth가 있으면 재확인) */
    if (img.__wt_classified__ && img.naturalWidth > 0) return;

    forceLoadSrc(img);

    function tryApply() {
      const nw = img.naturalWidth, nh = img.naturalHeight;
      if (nw > 0 && nh > 0) {
        applyClass(img, nw, nh);
      } else if (img.offsetWidth > 0) {
        /* naturalWidth 미확정 → offsetWidth 기준으로 일단 분류, load 후 재분류 */
        const ow = img.offsetWidth, oh = img.offsetHeight || img.offsetWidth * 2;
        applyClass(img, ow, oh);
      } else {
        /* 크기 불명 → 일단 narrow로 설정, 로드 완료 후 재분류 */
        img.classList.remove('__wt_wide__', '__wt_narrow__');
        img.classList.add('__wt_narrow__');
      }
    }

    if (img.complete && img.naturalWidth > 0) {
      tryApply();
    } else {
      /* 일단 narrow로 placeholder 설정 */
      if (!img.__wt_classified__) {
        img.classList.remove('__wt_wide__', '__wt_narrow__');
        img.classList.add('__wt_narrow__');
      }
      img.addEventListener('load', function onLoad() {
        img.removeEventListener('load', onLoad);
        tryApply();
      }, { once: true });
      img.addEventListener('error', function onErr() {
        img.removeEventListener('error', onErr);
        /* 에러 이미지는 wide로 표시 */
        img.classList.remove('__wt_wide__', '__wt_narrow__');
        img.classList.add('__wt_wide__');
        img.__wt_classified__ = true;
      }, { once: true });
    }
  }

  /* ── 컨테이너 flex 변환 ── */
  function setupContainer(el) {
    if (!el.__wt_cont_set__) {
      el.__wt_cont_set__ = true;
      el.classList.add('__wt_cont__');
      el.style.setProperty('overflow', 'visible', 'important');
      /* 부모 체인에서 overflow:hidden 제거 */
      let p = el.parentElement;
      let depth = 0;
      while (p && depth < 5) {
        const ov = getComputedStyle(p).overflow;
        if (ov === 'hidden') p.style.setProperty('overflow', 'visible', 'important');
        p = p.parentElement; depth++;
      }
    }
    /* 컨테이너 안 모든 img 분류 (새로 추가된 img도 처리) */
    el.querySelectorAll('img').forEach(classifyImg);
  }

  /* ── 최상위 탐색 ── */
  function findAndProcess() {
    let found = false;

    /* 1) 플랫폼 전용 컨테이너 */
    for (const sel of CONT_SELS) {
      try {
        document.querySelectorAll(sel).forEach(el => {
          setupContainer(el);
          found = true;
        });
      } catch(e) {}
    }

    /* 2) 플랫폼 전용 img → 부모를 컨테이너로 */
    if (!found) {
      for (const sel of IMG_SELS) {
        try {
          document.querySelectorAll(sel).forEach(img => {
            const p = img.parentElement;
            if (p) setupContainer(p);
            else classifyImg(img);
            found = true;
          });
        } catch(e) {}
      }
    }

    /* 3) Fallback: 화면 너비 25% 이상인 img를 웹툰 컷으로 간주 */
    if (!found) {
      const threshold = window.innerWidth * 0.25;
      document.querySelectorAll('img').forEach(img => {
        const w = img.offsetWidth || img.naturalWidth || 0;
        if (w >= threshold) {
          const p = img.parentElement;
          if (p) setupContainer(p);
          else classifyImg(img);
          found = true;
        }
      });
    }
  }

  /* ── IntersectionObserver: 뷰포트 진입 시 lazy img 강제 로드 & 재분류 ── */
  if (!window.__wt_io__ && 'IntersectionObserver' in window) {
    window.__wt_io__ = new IntersectionObserver(function(entries) {
      entries.forEach(function(entry) {
        if (entry.isIntersecting) {
          const img = entry.target;
          forceLoadSrc(img);
          if (!img.__wt_classified__ || img.naturalWidth === 0) classifyImg(img);
        }
      });
    }, { rootMargin: '200px' });
  }

  /* ── MutationObserver: DOM 변경(무한스크롤·동적 컷) 감지 ── */
  if (!window.__wt_mo__) {
    window.__wt_mo__ = new MutationObserver(function(muts) {
      let hasNew = false;
      muts.forEach(function(m) {
        m.addedNodes.forEach(function(n) {
          if (n.nodeType !== 1) return;
          hasNew = true;
          /* 새 img는 IntersectionObserver에 등록 */
          if (n.tagName === 'IMG') {
            classifyImg(n);
            if (window.__wt_io__) window.__wt_io__.observe(n);
          }
          n.querySelectorAll && n.querySelectorAll('img').forEach(img => {
            classifyImg(img);
            if (window.__wt_io__) window.__wt_io__.observe(img);
          });
        });
        /* 기존 img의 src 변경 감지 (lazy load 트리거) */
        if (m.type === 'attributes' && m.target.tagName === 'IMG') {
          const img = m.target;
          img.__wt_classified__ = false;
          classifyImg(img);
        }
      });
      if (hasNew) findAndProcess();
    });
    window.__wt_mo__.observe(document.body, {
      childList: true, subtree: true,
      attributes: true, attributeFilter: ['src','data-src','data-lazy-src']
    });
  }

  /* ── 기존 img 모두 IntersectionObserver 등록 ── */
  function registerAllImgs() {
    if (!window.__wt_io__) return;
    document.querySelectorAll('img').forEach(img => {
      window.__wt_io__.observe(img);
    });
  }

  /* ── 즉시 실행 ── */
  findAndProcess();
  registerAllImgs();

  /* ── 지연 재실행 (다양한 lazy-load 타이밍 대응) ── */
  [500, 1200, 2500, 4500, 8000].forEach(t => setTimeout(() => {
    findAndProcess();
    registerAllImgs();
  }, t));

  return 'WebtoonLayout:OK';
})();
        """.trimIndent()
    }

    fun buildResetScript(): String = """
(function() {
  const style = document.getElementById('__wt_style__');
  if (style) style.remove();

  document.querySelectorAll('.__wt_cont__').forEach(el => {
    el.classList.remove('__wt_cont__');
    el.__wt_cont_set__ = false;
  });
  document.querySelectorAll('.__wt_wide__, .__wt_narrow__').forEach(img => {
    img.classList.remove('__wt_wide__', '__wt_narrow__');
    img.__wt_classified__ = false;
    img.__wt_src_forced__  = false;
  });

  if (window.__wt_mo__)  { window.__wt_mo__.disconnect();  window.__wt_mo__  = null; }
  if (window.__wt_io__)  { window.__wt_io__.disconnect();  window.__wt_io__  = null; }

  return 'WebtoonLayout:RESET';
})();
    """.trimIndent()

    fun detectPlatform(url: String): WebtoonPlatform {
        val lower = url.lowercase()
        return when {
            "comic.naver.com"  in lower || "webtoon.naver.com" in lower -> WebtoonPlatform.NAVER
            "webtoon.kakao.com" in lower || "kakaopage.com"    in lower -> WebtoonPlatform.KAKAO
            "lezhin.com"       in lower                                 -> WebtoonPlatform.LEZHIN
            "bomtoon.com"      in lower                                 -> WebtoonPlatform.BOMTOON
            "toomics.com"      in lower                                 -> WebtoonPlatform.TOOMICS
            "tapas.io"         in lower                                 -> WebtoonPlatform.TAPAS
            "webtoons.com"     in lower                                 -> WebtoonPlatform.LINE
            else                                                        -> WebtoonPlatform.GENERIC
        }
    }
}
