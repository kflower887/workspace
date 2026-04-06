package com.foldablebrowser.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.foldablebrowser.app.R
import com.foldablebrowser.app.browser.BrowserSettings
import com.foldablebrowser.app.browser.BrowserViewModel
import com.foldablebrowser.app.browser.FoldableBrowserController
import com.foldablebrowser.app.browser.FoldableMode
import com.foldablebrowser.app.browser.SyncScrollWebView
import com.foldablebrowser.app.databinding.ActivityMainBinding
import com.foldablebrowser.app.webtoon.WebtoonLayoutEngine
import com.foldablebrowser.app.webtoon.WebtoonPlatform

/**
 * 폴더블 브라우저 메인 액티비티 v4
 *
 * ■ 일반 모드  : 분할 WebView (SINGLE/DUAL/TRIPLE), lockOffset 스크롤 연동
 * ■ 웹툰 모드  : 단일 풀스크린 WebtoonWebView + JS 컷 비율 감지 2열 레이아웃
 *   - 와이드컷(ratio > 1.2) → 100% 너비 단독 행
 *   - 세로컷(ratio ≤ 1.2)  → 50% 너비 2열 배치 → 스크롤 총량 ≈ 절반
 *   - 플랫폼 자동 감지 (네이버/카카오/레진/봄툰/투믹스/LINE/Tapas/범용)
 *   - 수동 플랫폼 선택 지원
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var browserController: FoldableBrowserController

    // ── 웹툰 모드 상태 ──────────────────────────────────────────────
    private var isWebtoonMode = false

    private val tabManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(TabManagerActivity.EXTRA_RESULT_ACTION)
            val index  = data.getIntExtra(TabManagerActivity.EXTRA_RESULT_INDEX, 0)
            when (action) {
                TabManagerActivity.RESULT_TAB_SELECTED -> { viewModel.switchTab(index); loadActiveTabUrl() }
                TabManagerActivity.RESULT_TAB_CLOSED   -> { viewModel.closeTab(index);  loadActiveTabUrl() }
                TabManagerActivity.RESULT_NEW_TAB      -> {
                    viewModel.addTab()
                    navigateToUrl(viewModel.settings.value?.homePage ?: "https://www.google.com")
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 생명주기
    // ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        browserController = FoldableBrowserController(this)

        setupWebtoonWebView()
        setupBrowserController()
        setupAddressBar()
        setupNavigationButtons()
        setupBottomBar()
        setupTopControls()
        observeViewModel()

        if (savedInstanceState == null) {
            viewModel.addTab(viewModel.settings.value?.homePage ?: "https://www.google.com")
            applyMode(viewModel.foldableMode.value ?: FoldableMode.DUAL, loadUrl = true)
        } else {
            applyMode(viewModel.foldableMode.value ?: FoldableMode.DUAL, loadUrl = false)
            val url = viewModel.getActiveTab()?.url
            if (!url.isNullOrEmpty()) browserController.loadUrl(url)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rearrangePanels()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (isWebtoonMode) {
            if (binding.webtoonWebView.canGoBack()) { binding.webtoonWebView.goBack(); return }
        } else {
            if (browserController.goBack()) return
        }
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserController.destroy()
        binding.webtoonWebView.destroy()
    }

    // ─────────────────────────────────────────────────────────────
    // 웹툰 WebView 초기화
    // ─────────────────────────────────────────────────────────────

    private fun setupWebtoonWebView() {
        binding.webtoonWebView.initialize()

        binding.webtoonWebView.onLayoutApplied = { wide, narrow ->
            runOnUiThread {
                viewModel.webtoonStats.value = wide to narrow
                if (wide + narrow > 0) {
                    binding.tvWebtoonBadge.text =
                        "🖼 웹툰 최적화 — 와이드 $wide / 세로 $narrow (2열) 컷"
                    binding.webtoonBadge.visibility = View.VISIBLE
                    // 3초 후 배지 자동 숨김
                    binding.webtoonBadge.postDelayed({
                        binding.webtoonBadge.visibility = View.GONE
                    }, 3000)
                }
            }
        }

        binding.webtoonWebView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: android.webkit.WebView, url: String) {
                super.onPageFinished(view, url)
                runOnUiThread {
                    binding.progressBar.visibility = View.GONE
                    viewModel.isLoading.value = false
                    viewModel.currentUrl.value = url
                    binding.etAddressBar.setText(extractDisplayUrl(url))
                    viewModel.canGoBack.value    = view.canGoBack()
                    viewModel.canGoForward.value = view.canGoForward()

                    val title = view.title ?: url
                    viewModel.addHistory(title, url)
                    updateBookmarkIcon(url)

                    // 페이지 완료 후 레이아웃 자동 재적용 (이미 웹툰 모드면)
                    if (isWebtoonMode) {
                        val platform = WebtoonLayoutEngine.detectPlatform(url)
                        viewModel.webtoonPlatform.value = platform
                        updatePlatformButton(platform)
                        // 약간 지연 후 주입 (일부 플랫폼 JS 렌더 대기)
                        binding.webtoonWebView.postDelayed({
                            binding.webtoonWebView.injectWebtoonLayout(platform)
                        }, 500)
                    }
                }
            }

            override fun onPageStarted(view: android.webkit.WebView, url: String, favicon: android.graphics.Bitmap?) {
                runOnUiThread {
                    binding.progressBar.visibility = View.VISIBLE
                    binding.progressBar.progress = 0
                    viewModel.isLoading.value = true
                    viewModel.currentUrl.value = url
                    binding.etAddressBar.setText(url)
                    // 새 페이지 → 배지 숨김
                    binding.webtoonBadge.visibility = View.GONE
                }
            }

            override fun shouldOverrideUrlLoading(
                view: android.webkit.WebView, request: WebResourceRequest
            ): Boolean = false
        }

        binding.webtoonWebView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: android.webkit.WebView, newProgress: Int) {
                runOnUiThread {
                    binding.progressBar.progress = newProgress
                    if (newProgress == 100) binding.progressBar.visibility = View.GONE
                }
            }

            override fun onReceivedTitle(view: android.webkit.WebView, title: String) {
                runOnUiThread {
                    val tabs = viewModel.tabs.value ?: return@runOnUiThread
                    tabs.getOrNull(viewModel.activeTabIndex.value ?: 0)?.title = title
                    viewModel.tabs.value = tabs
                    updateTabCounter()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 웹툰 모드 ON/OFF
    // ─────────────────────────────────────────────────────────────

    private fun enableWebtoonMode() {
        isWebtoonMode = true
        viewModel.webtoonModeEnabled.value = true

        // 분할 컨테이너 숨기고 웹툰 컨테이너 표시
        binding.webViewContainer.visibility = View.GONE
        binding.webtoonContainer.visibility = View.VISIBLE

        // 연동 버튼 비활성화 (웹툰 모드에서는 단일 WebView라 불필요)
        binding.btnSyncScroll.alpha = 0.3f
        binding.btnSyncScroll.isEnabled = false

        // 현재 URL을 웹툰 WebView에 로드
        val currentUrl = viewModel.currentUrl.value
            ?: viewModel.getActiveTab()?.url
            ?: viewModel.settings.value?.homePage
            ?: "https://www.google.com"

        binding.webtoonWebView.loadUrl(currentUrl)
        viewModel.currentUrl.value = currentUrl

        // 플랫폼 감지 & 버튼 표시
        val platform = WebtoonLayoutEngine.detectPlatform(currentUrl)
        viewModel.webtoonPlatform.value = platform
        updatePlatformButton(platform)
        binding.btnPlatform.visibility = View.VISIBLE

        updateWebtoonButton(true)
        Toast.makeText(
            this,
            "🖼 웹툰 2열 모드 ON\n" +
            "세로컷(좁은 비율) → 좌우 2열 배치\n" +
            "와이드컷(넓은 비율) → 전체 너비\n" +
            "스크롤 총량이 약 절반으로 줄어듭니다\n" +
            "플랫폼: ${platform.displayName}",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun disableWebtoonMode() {
        isWebtoonMode = false
        viewModel.webtoonModeEnabled.value = false

        // 웹툰 레이아웃 복원
        binding.webtoonWebView.resetWebtoonLayout()

        // 패널 컨테이너 복원
        binding.webtoonContainer.visibility = View.GONE
        binding.webViewContainer.visibility = View.VISIBLE

        // 연동 버튼 재활성화
        binding.btnSyncScroll.alpha = 1f
        binding.btnSyncScroll.isEnabled = true

        // 플랫폼 버튼 숨기기
        binding.btnPlatform.visibility = View.GONE
        binding.tvWebtoonStats.visibility = View.GONE

        // 현재 URL을 분할 WebView에 재로드
        val currentUrl = viewModel.currentUrl.value ?: "https://www.google.com"
        browserController.loadUrl(currentUrl)

        updateWebtoonButton(false)
        Toast.makeText(this, "웹툰 모드 OFF — 일반 분할 모드로 복귀", Toast.LENGTH_SHORT).show()
    }

    private fun updateWebtoonButton(on: Boolean) {
        if (on) {
            binding.btnWebtoonMode.text = "🖼 웹툰 ON"
            binding.btnWebtoonMode.setTextColor(getColor(R.color.webtoon_on_color))
            binding.btnWebtoonMode.alpha = 1f
        } else {
            binding.btnWebtoonMode.text = "🖼 웹툰"
            binding.btnWebtoonMode.setTextColor(getColor(R.color.webtoon_off_color))
            binding.btnWebtoonMode.alpha = 0.8f
        }
    }

    private fun updatePlatformButton(platform: WebtoonPlatform) {
        binding.btnPlatform.text = platform.displayName.take(6)
    }

    private fun showPlatformSelector() {
        val platforms = WebtoonPlatform.entries.toTypedArray()
        val names = platforms.map { it.displayName }.toTypedArray()
        val current = viewModel.webtoonPlatform.value ?: WebtoonPlatform.GENERIC
        val currentIdx = platforms.indexOf(current)

        AlertDialog.Builder(this)
            .setTitle("웹툰 플랫폼 선택")
            .setSingleChoiceItems(names, currentIdx) { dialog, which ->
                val selected = platforms[which]
                viewModel.webtoonPlatform.value = selected
                updatePlatformButton(selected)
                // 선택한 플랫폼으로 즉시 재적용
                binding.webtoonWebView.injectWebtoonLayout(selected)
                dialog.dismiss()
                Toast.makeText(this, "${selected.displayName} 규칙 적용 중...", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // 패널 구성 (일반 모드)
    // ─────────────────────────────────────────────────────────────

    private fun applyMode(mode: FoldableMode, loadUrl: Boolean = true) {
        viewModel.switchMode(mode)
        binding.webViewContainer.removeAllViews()
        val panels = browserController.setupPanels(mode)
        arrangePanels(panels)
        updateModeButton(mode)
        if (loadUrl) {
            val url = viewModel.getActiveTab()?.url
            if (!url.isNullOrEmpty()) browserController.loadUrl(url)
        }
    }

    private fun rearrangePanels() {
        val panels = browserController.getPanels()
        if (panels.isEmpty()) return
        binding.webViewContainer.removeAllViews()
        arrangePanels(panels)
    }

    private fun arrangePanels(panels: List<SyncScrollWebView>) {
        if (panels.isEmpty()) return
        val container = binding.webViewContainer
        container.orientation = LinearLayout.HORIZONTAL
        if (panels.size == 1) {
            container.addView(panels[0],
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            return
        }
        panels.forEachIndexed { index, wv ->
            container.addView(wv,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f))
            if (index < panels.size - 1) {
                container.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(getColor(R.color.divider_color))
                })
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 상단 컨트롤바
    // ─────────────────────────────────────────────────────────────

    private fun setupTopControls() {
        // ── 스크롤 연동 버튼 ──
        binding.btnSyncScroll.setOnClickListener {
            val mode = viewModel.foldableMode.value ?: FoldableMode.SINGLE
            if (mode == FoldableMode.SINGLE) {
                Toast.makeText(this, "분할 모드에서만 사용 가능합니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (browserController.isSyncActive) {
                browserController.unlockSync()
                viewModel.syncScrollEnabled.value = false
                updateSyncButton(false)
                Toast.makeText(this, "🔓 연동 해제\n각 화면을 자유롭게 스크롤한 뒤\n원하는 위치에서 연동을 켜세요", Toast.LENGTH_LONG).show()
            } else {
                val offsets = browserController.lockSyncFromCurrentPositions()
                viewModel.syncScrollEnabled.value = true
                updateSyncButton(true)
                val desc = offsets.drop(1).mapIndexed { idx, off ->
                    val panel = if (idx == 0) "우측" else "${idx + 2}번째"
                    val scrollDesc = when {
                        off > 500  -> "${off}px — 다음 섹션부터"
                        off > 0    -> "${off}px 아래"
                        off < 0    -> "${-off}px 위"
                        else       -> "동일 위치"
                    }
                    "$panel: $scrollDesc"
                }.joinToString("\n")
                Toast.makeText(this, "🔗 연동 잠금!\n좌측 스크롤 시 우측이 따라옵니다\n$desc", Toast.LENGTH_LONG).show()
            }
        }

        // 연동 버튼 길게 누르면 도움말
        binding.btnSyncScroll.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("📌 스크롤 연동 사용법")
                .setMessage(
                    "【웹툰 분할 읽기 방법】\n\n" +
                    "1. 연동 OFF 상태에서 두 화면을 원하는 위치로 스크롤하세요\n\n" +
                    "2. 좌측 = 1페이지 시작\n   우측 = 2페이지 시작에 맞추세요\n\n" +
                    "3. [연동 ON] 버튼을 누르면 현재 위치 차이가 고정됩니다\n\n" +
                    "4. 이후 좌측을 스크롤하면 우측이 자동으로 따라옵니다\n\n" +
                    "5. 새 페이지 로드 시 연동은 자동 해제됩니다"
                )
                .setPositiveButton("확인", null)
                .show()
            true
        }

        // ── 웹툰 모드 버튼 ──
        binding.btnWebtoonMode.setOnClickListener {
            if (isWebtoonMode) disableWebtoonMode() else enableWebtoonMode()
        }

        // ── 플랫폼 선택 버튼 ──
        binding.btnPlatform.setOnClickListener { showPlatformSelector() }

        // ── 화면 회전 버튼 ──
        binding.btnRotate.setOnClickListener {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (isLandscape)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        updateSyncButton(false)
        updateWebtoonButton(false)
    }

    private fun updateSyncButton(locked: Boolean) {
        if (locked) {
            binding.btnSyncScroll.text = "연동 ON"
            binding.btnSyncScroll.alpha = 1f
            binding.btnSyncScroll.setTextColor(getColor(R.color.sync_on_color))
        } else {
            binding.btnSyncScroll.text = "연동 OFF"
            binding.btnSyncScroll.alpha = 0.6f
            binding.btnSyncScroll.setTextColor(getColor(R.color.sync_off_color))
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 브라우저 컨트롤러 콜백 (일반 모드용)
    // ─────────────────────────────────────────────────────────────

    private fun setupBrowserController() {
        browserController.onPageStarted = { url ->
            runOnUiThread {
                binding.progressBar.visibility = View.VISIBLE
                viewModel.isLoading.value = true
                viewModel.currentUrl.value = url
                binding.etAddressBar.setText(url)
                viewModel.canGoBack.value    = browserController.canGoBack()
                viewModel.canGoForward.value = browserController.canGoForward()
            }
        }
        browserController.onPageFinished = { url ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                viewModel.isLoading.value = false
                viewModel.currentUrl.value = url
                binding.etAddressBar.setText(extractDisplayUrl(url))
                viewModel.canGoBack.value    = browserController.canGoBack()
                viewModel.canGoForward.value = browserController.canGoForward()

                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                tabs.getOrNull(viewModel.activeTabIndex.value ?: 0)?.url = url
                viewModel.tabs.value = tabs

                updateSyncButton(browserController.isSyncActive)
                viewModel.addHistory(browserController.getTitle().ifBlank { url }, url)
                updateBookmarkIcon(url)
            }
        }
        browserController.onTitleReceived = { title ->
            runOnUiThread {
                viewModel.pageTitle.value = title
                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                tabs.getOrNull(viewModel.activeTabIndex.value ?: 0)?.title = title
                viewModel.tabs.value = tabs
                updateTabCounter()
            }
        }
        browserController.onProgressChanged = { progress ->
            runOnUiThread {
                binding.progressBar.progress = progress
                viewModel.loadProgress.value = progress
            }
        }
        browserController.onReceivedIcon = { icon ->
            runOnUiThread {
                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                tabs.getOrNull(viewModel.activeTabIndex.value ?: 0)?.favicon = icon
                viewModel.tabs.value = tabs
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 주소창
    // ─────────────────────────────────────────────────────────────

    private fun setupAddressBar() {
        binding.etAddressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val input = binding.etAddressBar.text.toString().trim()
                if (input.isNotEmpty()) { navigateToUrl(input); hideKeyboard() }
                true
            } else false
        }
        binding.etAddressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                binding.etAddressBar.setText(viewModel.currentUrl.value)
                binding.etAddressBar.selectAll()
            } else {
                binding.etAddressBar.setText(extractDisplayUrl(viewModel.currentUrl.value ?: ""))
            }
        }
        binding.btnRefresh.setOnClickListener {
            if (viewModel.isLoading.value == true) {
                if (isWebtoonMode) binding.webtoonWebView.stopLoading()
                else browserController.stopLoading()
            } else {
                if (isWebtoonMode) binding.webtoonWebView.reload()
                else browserController.reload()
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 네비게이션 버튼
    // ─────────────────────────────────────────────────────────────

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            val went = if (isWebtoonMode) {
                if (binding.webtoonWebView.canGoBack()) { binding.webtoonWebView.goBack(); true } else false
            } else browserController.goBack()
            if (!went) Toast.makeText(this, "이전 페이지가 없습니다", Toast.LENGTH_SHORT).show()
        }
        binding.btnForward.setOnClickListener {
            val went = if (isWebtoonMode) {
                if (binding.webtoonWebView.canGoForward()) { binding.webtoonWebView.goForward(); true } else false
            } else browserController.goForward()
            if (!went) Toast.makeText(this, "다음 페이지가 없습니다", Toast.LENGTH_SHORT).show()
        }
        binding.btnTabCounter.setOnClickListener { openTabManager() }
        binding.btnMenu.setOnClickListener { showBrowserMenu() }
    }

    // ─────────────────────────────────────────────────────────────
    // 하단 바
    // ─────────────────────────────────────────────────────────────

    private fun setupBottomBar() {
        binding.btnHome.setOnClickListener {
            navigateToUrl(viewModel.settings.value?.homePage ?: "https://www.google.com")
        }
        binding.btnBookmark.setOnClickListener { showBookmarkDialog() }
        binding.btnFoldableMode.setOnClickListener {
            if (isWebtoonMode) {
                Toast.makeText(this, "웹툰 모드를 먼저 끄고 분할 모드를 변경하세요", Toast.LENGTH_SHORT).show()
            } else {
                showModeSelectionDialog()
            }
        }
        binding.btnHistory.setOnClickListener { showHistoryDialog() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    // ─────────────────────────────────────────────────────────────
    // 분할 모드 선택
    // ─────────────────────────────────────────────────────────────

    private fun showModeSelectionDialog() {
        val cur = viewModel.foldableMode.value ?: FoldableMode.DUAL
        val items = arrayOf("📱 일반 모드 (단일 화면)", "📖 2단 분할 (좌/우)", "📒 3단 분할 (좌/중/우)")
        val checked = when (cur) { FoldableMode.SINGLE -> 0; FoldableMode.DUAL -> 1; else -> 2 }
        AlertDialog.Builder(this)
            .setTitle("분할 모드 선택")
            .setSingleChoiceItems(items, checked) { dialog, which ->
                val m = when (which) { 0 -> FoldableMode.SINGLE; 1 -> FoldableMode.DUAL; else -> FoldableMode.TRIPLE }
                applyMode(m, loadUrl = true)
                dialog.dismiss()
                Toast.makeText(this, "${items[which].drop(3)} 전환", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null).show()
    }

    private fun updateModeButton(mode: FoldableMode) {
        when (mode) {
            FoldableMode.SINGLE -> { binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_single); binding.tvFoldableMode.text = "일반" }
            FoldableMode.DUAL   -> { binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_dual);   binding.tvFoldableMode.text = "2단" }
            FoldableMode.TRIPLE -> { binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_triple); binding.tvFoldableMode.text = "3단" }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 히스토리
    // ─────────────────────────────────────────────────────────────

    private fun showHistoryDialog() {
        val list = viewModel.historyList.value ?: mutableListOf()
        if (list.isEmpty()) { Toast.makeText(this, "방문 기록이 없습니다", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("방문 기록")
            .setItems(list.map { "${it.title}\n${it.url}" }.toTypedArray()) { _, i -> navigateToUrl(list[i].url) }
            .setNeutralButton("전체 삭제") { _, _ ->
                AlertDialog.Builder(this).setMessage("방문 기록을 모두 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ -> viewModel.clearHistory(); Toast.makeText(this, "삭제 완료", Toast.LENGTH_SHORT).show() }
                    .setNegativeButton("취소", null).show()
            }
            .setNegativeButton("닫기", null).show()
    }

    // ─────────────────────────────────────────────────────────────
    // 북마크
    // ─────────────────────────────────────────────────────────────

    private fun showBookmarkDialog() {
        val url = viewModel.currentUrl.value ?: ""
        val isStarred = viewModel.isBookmarked(url)
        val options = mutableListOf<String>()
        if (url.isNotBlank()) options.add(if (isStarred) "★ 북마크 제거" else "☆ 현재 페이지 추가")
        options.add("북마크 목록 보기")
        AlertDialog.Builder(this)
            .setTitle("북마크")
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 && url.isNotBlank() -> {
                        if (isStarred) {
                            viewModel.bookmarkList.value?.find { it.url == url }?.id?.let { viewModel.removeBookmark(it) }
                            Toast.makeText(this, "북마크 제거됨", Toast.LENGTH_SHORT).show()
                        } else {
                            viewModel.addBookmark(browserController.getTitle().ifBlank { url }, url)
                            Toast.makeText(this, "북마크 추가됨", Toast.LENGTH_SHORT).show()
                        }
                        updateBookmarkIcon(url)
                    }
                    else -> showBookmarkList()
                }
            }
            .setNegativeButton("닫기", null).show()
    }

    private fun showBookmarkList() {
        val list = viewModel.bookmarkList.value ?: mutableListOf()
        if (list.isEmpty()) { Toast.makeText(this, "저장된 북마크가 없습니다", Toast.LENGTH_SHORT).show(); return }
        AlertDialog.Builder(this)
            .setTitle("북마크 목록")
            .setItems(list.map { "${it.title}\n${it.url}" }.toTypedArray()) { _, i -> navigateToUrl(list[i].url) }
            .setNeutralButton("편집") { _, _ ->
                val titles = list.map { it.title }.toTypedArray()
                val checked = BooleanArray(list.size)
                AlertDialog.Builder(this)
                    .setTitle("삭제할 북마크 선택")
                    .setMultiChoiceItems(titles, checked) { _, idx, v -> checked[idx] = v }
                    .setPositiveButton("삭제") { _, _ ->
                        list.filterIndexed { idx, _ -> checked[idx] }.forEach { viewModel.removeBookmark(it.id) }
                        Toast.makeText(this, "선택 항목 삭제됨", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("취소", null).show()
            }
            .setNegativeButton("닫기", null).show()
    }

    private fun updateBookmarkIcon(url: String) {
        binding.btnBookmark.setImageResource(
            if (viewModel.isBookmarked(url)) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 설정
    // ─────────────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val cur = viewModel.settings.value ?: BrowserSettings()
        val items = arrayOf(
            "홈페이지: ${cur.homePage}",
            "텍스트 크기: ${cur.textSize}%",
            "자바스크립트: ${if (cur.jsEnabled) "허용" else "차단"}",
            "데스크톱 모드: ${if (cur.desktopMode) "켜짐" else "꺼짐"}"
        )
        AlertDialog.Builder(this).setTitle("설정")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { val et = android.widget.EditText(this).apply { setText(cur.homePage) }
                           AlertDialog.Builder(this).setTitle("홈페이지").setView(et)
                               .setPositiveButton("저장") { _, _ ->
                                   val u = et.text.toString().trim().let { if (it.startsWith("http")) it else "https://$it" }
                                   viewModel.updateSettings(cur.copy(homePage = u)) }
                               .setNegativeButton("취소", null).show() }
                    1 -> { val sizes = arrayOf("75%","90%","100%","110%","125%","150%"); val vals = intArrayOf(75,90,100,110,125,150)
                           val idx = vals.indexOfFirst { it == cur.textSize }.takeIf { it >= 0 } ?: 2
                           AlertDialog.Builder(this).setTitle("텍스트 크기").setSingleChoiceItems(sizes, idx) { d, w ->
                               viewModel.updateSettings(cur.copy(textSize = vals[w])); d.dismiss() }.setNegativeButton("취소",null).show() }
                    2 -> { viewModel.updateSettings(cur.copy(jsEnabled = !cur.jsEnabled)); showSettingsDialog() }
                    3 -> { viewModel.updateSettings(cur.copy(desktopMode = !cur.desktopMode)); showSettingsDialog() }
                }
            }
            .setNegativeButton("닫기", null).show()
    }

    // ─────────────────────────────────────────────────────────────
    // 탭 매니저
    // ─────────────────────────────────────────────────────────────

    private fun openTabManager() {
        val tabs = viewModel.tabs.value ?: return
        tabManagerLauncher.launch(Intent(this, TabManagerActivity::class.java).apply {
            putExtra(TabManagerActivity.EXTRA_TABS, ArrayList(tabs))
            putExtra(TabManagerActivity.EXTRA_ACTIVE_TAB, viewModel.activeTabIndex.value ?: 0)
        })
    }

    private fun loadActiveTabUrl() {
        val url = viewModel.getActiveTab()?.url ?: return
        navigateToUrl(url)
    }

    // ─────────────────────────────────────────────────────────────
    // URL 네비게이션 (일반 + 웹툰 모드 공용)
    // ─────────────────────────────────────────────────────────────

    private fun navigateToUrl(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
        }
        val tabs = viewModel.tabs.value ?: return
        tabs.getOrNull(viewModel.activeTabIndex.value ?: 0)?.url = url
        viewModel.tabs.value = tabs

        if (isWebtoonMode) {
            binding.webtoonWebView.loadUrl(url)
        } else {
            browserController.loadUrl(url)
        }
        binding.etAddressBar.setText(extractDisplayUrl(url))
    }

    private fun extractDisplayUrl(url: String): String = try {
        android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) { url }

    // ─────────────────────────────────────────────────────────────
    // ViewModel 관찰
    // ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.canGoBack.observe(this)    { can -> binding.btnBack.alpha    = if (can) 1f else 0.4f; binding.btnBack.isEnabled    = can }
        viewModel.canGoForward.observe(this) { can -> binding.btnForward.alpha = if (can) 1f else 0.4f; binding.btnForward.isEnabled = can }
        viewModel.isLoading.observe(this)    { loading -> binding.btnRefresh.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_refresh) }
        viewModel.tabs.observe(this)         { updateTabCounter() }
        viewModel.webtoonStats.observe(this) { (wide, narrow) ->
            if (wide + narrow > 0) {
                binding.tvWebtoonStats.text = "와이드 $wide / 세로 $narrow"
                binding.tvWebtoonStats.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTabCounter() { binding.tvTabCount.text = viewModel.getTabCount().toString() }

    // ─────────────────────────────────────────────────────────────
    // 브라우저 메뉴
    // ─────────────────────────────────────────────────────────────

    private fun showBrowserMenu() {
        val items = arrayOf("새 탭", "새로고침", "북마크 추가", "페이지 공유", "히스토리", "설정")
        AlertDialog.Builder(this).setItems(items) { _, which ->
            when (which) {
                0 -> { viewModel.addTab(); navigateToUrl(viewModel.settings.value?.homePage ?: "https://www.google.com") }
                1 -> { if (isWebtoonMode) binding.webtoonWebView.reload() else browserController.reload() }
                2 -> {
                    val url = viewModel.currentUrl.value ?: ""
                    val title = if (isWebtoonMode) binding.webtoonWebView.title ?: url else browserController.getTitle()
                    viewModel.addBookmark(title, url); updateBookmarkIcon(url)
                    Toast.makeText(this, "북마크 추가됨", Toast.LENGTH_SHORT).show()
                }
                3 -> startActivity(Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, viewModel.currentUrl.value) },
                    "페이지 공유"))
                4 -> showHistoryDialog()
                5 -> showSettingsDialog()
            }
        }.show()
    }

    // ─────────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }
}
