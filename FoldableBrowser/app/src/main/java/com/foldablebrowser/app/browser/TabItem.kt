package com.foldablebrowser.app.browser

import android.graphics.Bitmap
import java.io.Serializable

/**
 * 브라우저 탭 데이터 모델
 */
data class TabItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    var title: String = "새 탭",
    var url: String = "",
    @Transient var favicon: Bitmap? = null,
    var scrollY: Int = 0,
    var isActive: Boolean = false
) : Serializable
