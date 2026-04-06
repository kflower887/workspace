package com.foldablebrowser.app.webtoon

/**
 * 웹툰 플랫폼별 이미지 셀렉터 규칙
 *
 * imageSelectors    : 웹툰 컷 <img> 태그를 직접 가리키는 CSS 셀렉터 목록
 * containerSelectors: 컷들을 감싸는 wrapper 엘리먼트 셀렉터 목록
 *                    (wrapper를 flex-container로 변환하면 하위 img가 자동 배치됨)
 */
enum class WebtoonPlatform(
    val displayName: String,
    val imageSelectors: List<String>,
    val containerSelectors: List<String>
) {

    /** 네이버 웹툰 (comic.naver.com, webtoon.naver.com) */
    NAVER(
        displayName = "네이버 웹툰",
        imageSelectors = listOf(
            ".wt_viewer img",
            "#comic_view_area img",
            ".view_img img",
            "._imageList img",
            ".comicList img"
        ),
        containerSelectors = listOf(
            ".wt_viewer",
            "#comic_view_area",
            ".view_img",
            "._imageList",
            ".comicList"
        )
    ),

    /** 카카오웹툰 / 카카오페이지 */
    KAKAO(
        displayName = "카카오 웹툰",
        imageSelectors = listOf(
            ".viewer-layer img",
            ".imageWrapper img",
            ".scroll-images img",
            "[class*='viewer'] img",
            "[class*='episode'] img"
        ),
        containerSelectors = listOf(
            ".viewer-layer",
            ".imageWrapper",
            ".scroll-images",
            "[class*='EpisodeViewer']",
            "[class*='episode-viewer']"
        )
    ),

    /** 레진코믹스 */
    LEZHIN(
        displayName = "레진코믹스",
        imageSelectors = listOf(
            ".content-item img",
            ".ep-content img",
            "[data-type='comic'] img",
            ".viewer img"
        ),
        containerSelectors = listOf(
            ".content-item",
            ".ep-content",
            ".lz-view",
            ".viewer-content"
        )
    ),

    /** 봄툰 */
    BOMTOON(
        displayName = "봄툰",
        imageSelectors = listOf(
            ".viewer_img img",
            ".cartoon_view img",
            "#viewer img"
        ),
        containerSelectors = listOf(
            ".viewer_img",
            ".cartoon_view",
            "#viewer"
        )
    ),

    /** 투믹스 */
    TOOMICS(
        displayName = "투믹스",
        imageSelectors = listOf(
            ".swiper-slide img",
            ".view_img img",
            ".comicView img"
        ),
        containerSelectors = listOf(
            ".swiper-wrapper",
            ".view_img",
            ".comicView"
        )
    ),

    /** 타파스 (영어권) */
    TAPAS(
        displayName = "Tapas",
        imageSelectors = listOf(
            ".js-episode-img",
            ".episode-view img",
            ".content-img"
        ),
        containerSelectors = listOf(
            ".episode-view",
            ".content-view",
            ".js-episode-body"
        )
    ),

    /** LINE Webtoon (webtoons.com) */
    LINE(
        displayName = "LINE Webtoon",
        imageSelectors = listOf(
            "#content img",
            "._3HZ-S img",
            ".viewer-img"
        ),
        containerSelectors = listOf(
            "#content",
            "._3HZ-S",
            ".viewer-content"
        )
    ),

    /**
     * 범용 (GENERIC) — 알 수 없는 사이트의 fallback.
     * 화면 너비 30% 이상의 이미지를 모두 웹툰 컷으로 간주.
     * JS 내부에서 직접 처리하므로 셀렉터는 빈 리스트.
     */
    GENERIC(
        displayName = "범용 (자동 감지)",
        imageSelectors = emptyList(),
        containerSelectors = emptyList()
    );
}
