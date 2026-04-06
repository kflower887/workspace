package com.foldablebrowser.app.ui

import android.app.Activity
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowInsetsController
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.foldablebrowser.app.R
import com.foldablebrowser.app.browser.BrowserSettings
import com.foldablebrowser.app.browser.BrowserViewModel
import com.foldablebrowser.app.browser.FoldableBrowserController
import com.foldablebrowser.app.browser.FoldableMode
import com.foldablebrowser.app.browser.SyncScrollWebView
import com.foldablebrowser.app.databinding.ActivityMainBinding

/**
 * 폴더블 브라우저 메인 액티비티 v5
 *
 * ■ 일반 모드 : SINGLE / DUAL / TRIPLE 분할 WebView
 *   - 연동 OFF: 각 패널 독립 스크롤
 *   - 연동 ON : 사용자가 원하는 위치에서 잠금(lockOffset) 후 마스터 추종
 *
 * ■ 웹툰 모드 (DUAL 분할 기반)
 *   - 좌측 = 1페이지(상단), 우측 = 2페이지(좌측 바로 다음)
 *   - 페이지 로드 완료 → 우측 패널을 "패널 높이(px)" 만큼 자동 오프셋
 *   - 이후 좌측 스크롤 → 우측 자동 추종 (이음새 없는 연속 읽기)
 *   - [연동 재설정] 버튼으로 언제든 오프셋 재계산
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var browserController: FoldableBrowserController

    private var isWebtoonMode = false
    private var isFullscreen  = false

    // 전체화면 시 UI 자동 복귀 Runnable
    private val fullscreenHintHideRunnable = Runnable {
        binding.fullscreenHint.animate().alpha(0f).setDuration(400).withEndAction {
            binding.fullscreenHint.visibility = View.GONE
        }.start()
    }

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

        setupBrowserController()
        setupAddressBar()
        setupNavigationButtons()
        setupBottomBar()
        setupTopControls()
        setupFullscreenTapToRestore()
        observeViewModel()

        // 웹툰 컨테이너는 사용 안 함 (DUAL 모드 기반으로 전환)
        binding.webtoonContainer.visibility = View.GONE

        if (savedInstanceState == null) {
            viewModel.addTab(viewModel.settings.value?.homePage ?: "https://www.google.com")
            applyMode(FoldableMode.DUAL, loadUrl = true)
        } else {
            applyMode(viewModel.foldableMode.value ?: FoldableMode.DUAL, loadUrl = false)
            val url = viewModel.getActiveTab()?.url
            if (!url.isNullOrEmpty()) browserController.loadUrl(url)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rearrangePanels()
        // 화면 회전 시 웹툰 모드면 패널 높이 재기준으로 재배치
        if (isWebtoonMode) {
            binding.webViewContainer.post {
                if (browserController.isSyncActive) {
                    browserController.lockWebtoonSync()
                } else {
                    browserController.initWebtoonPageMode()
                }
            }
        }
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (isFullscreen) { exitFullscreen(); return }
        if (browserController.goBack()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserController.destroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // 다이얼로그/알림 등으로 포커스 잃었다가 복귀할 때 전체화면 유지
        if (hasFocus && isFullscreen) applyImmersive()
    }

    // ─────────────────────────────────────────────────────────────
    // 웹툰 모드 ON / OFF
    // ─────────────────────────────────────────────────────────────

    private fun enableWebtoonMode() {
        isWebtoonMode = true
        viewModel.webtoonModeEnabled.value = true

        // DUAL 모드로 전환 (이미 DUAL이면 패널 재사용)
        val currentMode = viewModel.foldableMode.value ?: FoldableMode.SINGLE
        if (currentMode != FoldableMode.DUAL) {
            applyMode(FoldableMode.DUAL, loadUrl = true)
        }

        // 연동 버튼 초기 상태 (웹툰 모드 - 연동 OFF 상태로 시작)
        updateSyncButton(active = false, webtoonMode = true)

        // 패널 레이아웃 완료 후 페이지 초기 배치
        binding.webViewContainer.post {
            browserController.initWebtoonPageMode()
        }

        // 좌/우 네비게이션 오버레이 버튼 표시
        binding.webtoonNavLeft.visibility  = View.VISIBLE
        binding.webtoonNavRight.visibility = View.VISIBLE

        updateWebtoonButton(true)
        Toast.makeText(
            this,
            "📖 웹툰 모드 ON\n" +
            "좌측 = 1페이지 / 우측 = 2페이지\n" +
            "하단 [이전/다음] 버튼으로 페이지를 넘기세요",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun disableWebtoonMode() {
        isWebtoonMode = false
        viewModel.webtoonModeEnabled.value = false
        browserController.unlockWebtoonSync()
        updateSyncButton(active = false, webtoonMode = false)
        updateWebtoonButton(false)
        // 좌/우 네비게이션 오버레이 버튼 숨김
        binding.webtoonNavLeft.visibility  = View.GONE
        binding.webtoonNavRight.visibility = View.GONE
        Toast.makeText(this, "웹툰 모드 OFF", Toast.LENGTH_SHORT).show()
    }

    private fun updateWebtoonButton(on: Boolean) {
        binding.btnWebtoonMode.text = if (on) "📖 웹툰 ON" else "📖 웹툰"
        binding.btnWebtoonMode.alpha = if (on) 1f else 0.8f
        binding.btnWebtoonMode.setTextColor(
            getColor(if (on) R.color.webtoon_on_color else R.color.webtoon_off_color)
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 패널 구성
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

            if (isWebtoonMode) {
                // 웹툰 모드: 연동 ON/OFF 토글
                if (browserController.isSyncActive) {
                    // 연동 OFF → 각 패널 독립 스크롤 (현재 위치 유지)
                    browserController.unlockWebtoonSync()
                    updateSyncButton(active = false, webtoonMode = true)
                    Toast.makeText(this,
                        "🔓 연동 OFF\n각 화면을 자유롭게 스크롤하세요\n[이전]/[다음] 버튼은 계속 작동합니다",
                        Toast.LENGTH_SHORT).show()
                } else {
                    // 연동 ON → 현재 좌측 위치 기준 우측 offset=panelH 고정
                    browserController.lockWebtoonSync()
                    updateSyncButton(active = true, webtoonMode = true)
                    val panelH = browserController.getMasterView()?.height ?: 0
                    Toast.makeText(this,
                        "🔗 연동 ON\n좌측 스크롤 시 우측이 ${panelH}px 뒤에서 따라옵니다\n버튼으로 페이지 이동도 가능합니다",
                        Toast.LENGTH_SHORT).show()
                }
            } else {
                // 일반 모드: 현재 위치 기준 잠금
                if (browserController.isSyncActive) {
                    browserController.unlockSync()
                    updateSyncButton(active = false, webtoonMode = false)
                    Toast.makeText(this, "🔓 연동 해제\n각 화면을 자유롭게 스크롤하세요", Toast.LENGTH_SHORT).show()
                } else {
                    val offsets = browserController.lockSyncFromCurrentPositions()
                    updateSyncButton(active = true, webtoonMode = false)
                    val desc = offsets.drop(1).mapIndexed { i, off ->
                        val p = if (i == 0) "우측" else "${i + 2}번째"
                        val d = when {
                            off > 0  -> "${off}px 아래"
                            off < 0  -> "${-off}px 위"
                            else     -> "동일 위치"
                        }
                        "$p: $d"
                    }.joinToString("\n")
                    Toast.makeText(this, "🔗 연동 잠금!\n$desc", Toast.LENGTH_LONG).show()
                }
            }
        }

        // 연동 버튼 길게 누르기 → 도움말
        binding.btnSyncScroll.setOnLongClickListener {
            AlertDialog.Builder(this)
                .setTitle("📌 스크롤 연동 사용법")
                .setMessage(
                    if (isWebtoonMode)
                        "【웹툰 모드 사용법】\n\n" +
                        "▼ [이전] / [다음] 버튼\n" +
                        "  • 어느 쪽 버튼이든 좌/우 동시 이동\n" +
                        "  • 현재 위치 기준 ±화면 높이만큼 이동\n" +
                        "  • 연동 ON/OFF 관계없이 항상 동작\n\n" +
                        "▼ 연동 OFF (기본)\n" +
                        "  • 각 패널 독립 스크롤 가능\n" +
                        "  • 버튼 클릭 → 좌=현재+1페이지, 우=좌+1페이지\n\n" +
                        "▼ 연동 ON\n" +
                        "  • 좌측 드래그 시 우측이 1페이지 뒤에서 자동 추종\n" +
                        "  • 버튼 클릭 → 좌 이동 후 우측 자동 따라옴\n\n" +
                        "예시 (A~F): 처음=좌A/우B → [다음]=좌C/우D → [다음]=좌E/우F"
                    else
                        "【일반 연동】\n\n" +
                        "1. 연동 OFF 상태에서 두 화면을 원하는 위치로 이동\n" +
                        "2. [연동 OFF] 버튼을 눌러 현재 위치 차이를 고정\n" +
                        "3. 이후 좌측 스크롤 시 우측이 고정된 차이만큼 따라옵니다\n" +
                        "4. 새 페이지 로드 시 연동이 해제됩니다"
                )
                .setPositiveButton("확인", null)
                .show()
            true
        }

        // ── 웹툰 모드 버튼 ──
        binding.btnWebtoonMode.setOnClickListener {
            if (isWebtoonMode) disableWebtoonMode() else enableWebtoonMode()
        }

        // ── 웹툰 페이지 네비게이션 버튼 (좌/우 어디서 눌러도 동일하게 양쪽 동시 이동) ──
        binding.btnLeftPrev.setOnClickListener  { browserController.webtoonPagePrev() }
        binding.btnLeftNext.setOnClickListener  { browserController.webtoonPageNext() }
        binding.btnRightPrev.setOnClickListener { browserController.webtoonPagePrev() }
        binding.btnRightNext.setOnClickListener { browserController.webtoonPageNext() }

        // ── 플랫폼 버튼 숨김 (웹툰 모드 재설계로 불필요) ──
        binding.btnPlatform.visibility = View.GONE

        // ── 화면 회전 버튼 ──
        binding.btnRotate.setOnClickListener {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            requestedOrientation = if (isLandscape)
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            else
                ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }

        // ── 전체화면 버튼 ──
        binding.btnFullscreen.setOnClickListener {
            if (isFullscreen) exitFullscreen() else enterFullscreen()
        }

        updateSyncButton(active = false, webtoonMode = false)
        updateWebtoonButton(false)
        updateFullscreenButton(false)
    }

    private fun updateSyncButton(active: Boolean, webtoonMode: Boolean) {
        when {
            active && webtoonMode -> {
                binding.btnSyncScroll.text = "📖 연동 ON"
                binding.btnSyncScroll.alpha = 1f
                binding.btnSyncScroll.setTextColor(getColor(R.color.webtoon_on_color))
            }
            active -> {
                binding.btnSyncScroll.text = "연동 ON"
                binding.btnSyncScroll.alpha = 1f
                binding.btnSyncScroll.setTextColor(getColor(R.color.sync_on_color))
            }
            else -> {
                binding.btnSyncScroll.text = "연동 OFF"
                binding.btnSyncScroll.alpha = 0.6f
                binding.btnSyncScroll.setTextColor(getColor(R.color.sync_off_color))
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 브라우저 컨트롤러 콜백
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

                viewModel.addHistory(browserController.getTitle().ifBlank { url }, url)
                updateBookmarkIcon(url)

                // 웹툰 모드: 페이지 로드 완료 후 배치 재적용
                if (isWebtoonMode) {
                    binding.webViewContainer.postDelayed({
                        if (browserController.isSyncActive) {
                            // 연동 ON이었으면 연동 재적용
                            browserController.lockWebtoonSync()
                            updateSyncButton(active = true, webtoonMode = true)
                        } else {
                            // 연동 OFF면 초기 배치만
                            browserController.initWebtoonPageMode()
                        }
                    }, 800)
                }

                updateSyncButton(
                    active = browserController.isSyncActive,
                    webtoonMode = isWebtoonMode
                )
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
            if (viewModel.isLoading.value == true) browserController.stopLoading()
            else browserController.reload()
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 네비게이션 버튼
    // ─────────────────────────────────────────────────────────────

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            if (!browserController.goBack())
                Toast.makeText(this, "이전 페이지가 없습니다", Toast.LENGTH_SHORT).show()
        }
        binding.btnForward.setOnClickListener {
            if (!browserController.goForward())
                Toast.makeText(this, "다음 페이지가 없습니다", Toast.LENGTH_SHORT).show()
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
                Toast.makeText(this, "웹툰 모드를 끄고 분할 모드를 변경하세요", Toast.LENGTH_SHORT).show()
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
    // URL 네비게이션
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

        if (isWebtoonMode) browserController.loadUrlWebtoon(url)
        else              browserController.loadUrl(url)

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
                1 -> browserController.reload()
                2 -> {
                    val url = viewModel.currentUrl.value ?: ""
                    viewModel.addBookmark(browserController.getTitle().ifBlank { url }, url)
                    updateBookmarkIcon(url)
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
    // 전체화면 모드
    // ─────────────────────────────────────────────────────────────

    /** 전체화면 진입: 상단/하단 UI 숨김 + 상태바/네비바 immersive 숨김 */
    private fun enterFullscreen() {
        isFullscreen = true

        // 앱 UI 숨김 (애니메이션)
        binding.topToolbar.animate().translationY(-binding.topToolbar.height.toFloat())
            .setDuration(250).withEndAction { binding.topToolbar.visibility = View.GONE }.start()
        binding.controlBar.animate().translationY(-binding.controlBar.height.toFloat())
            .setDuration(250).withEndAction { binding.controlBar.visibility = View.GONE }.start()
        binding.bottomNavBar.animate().translationY(binding.bottomNavBar.height.toFloat())
            .setDuration(250).withEndAction { binding.bottomNavBar.visibility = View.GONE }.start()

        // 시스템 UI 숨김 (상태바 + 네비게이션바)
        applyImmersive()

        // 웹툰 네비 버튼은 전체화면에서도 유지
        updateFullscreenButton(true)

        // 상단 힌트 표시 후 자동 페이드아웃
        showFullscreenHint("⛶ 전체화면  |  탭하거나 ← 뒤로를 눌러 복귀")
    }

    /** 전체화면 해제 */
    private fun exitFullscreen() {
        isFullscreen = false

        // 앱 UI 복귀
        binding.topToolbar.visibility = View.VISIBLE
        binding.topToolbar.translationY = -binding.topToolbar.height.toFloat()
        binding.topToolbar.animate().translationY(0f).setDuration(250).start()

        binding.controlBar.visibility = View.VISIBLE
        binding.controlBar.translationY = -binding.controlBar.height.toFloat()
        binding.controlBar.animate().translationY(0f).setDuration(250).start()

        binding.bottomNavBar.visibility = View.VISIBLE
        binding.bottomNavBar.translationY = binding.bottomNavBar.height.toFloat()
        binding.bottomNavBar.animate().translationY(0f).setDuration(250).start()

        // 시스템 UI 복귀
        WindowCompat.setDecorFitsSystemWindows(window, true)
        WindowInsetsControllerCompat(window, binding.root).apply {
            show(WindowInsetsCompat.Type.systemBars())
        }

        binding.fullscreenHint.visibility = View.GONE
        updateFullscreenButton(false)
    }

    /** Immersive 모드 적용 (시스템 바 완전 숨김, 스와이프로 임시 표시) */
    private fun applyImmersive() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, binding.root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    /** 전체화면에서 콘텐츠 탭 시 UI 복귀 처리 */
    private fun setupFullscreenTapToRestore() {
        // contentFrame(WebView 영역) 탭 감지
        binding.contentFrame.setOnTouchListener { _, event ->
            if (isFullscreen && event.action == MotionEvent.ACTION_UP) {
                exitFullscreen()
            }
            false   // 이벤트 소비 안 함 → WebView 터치도 정상 동작
        }
    }

    /** 전체화면 힌트 토스트(상단 오버레이) 표시 후 자동 숨김 */
    private fun showFullscreenHint(msg: String) {
        binding.fullscreenHint.removeCallbacks(fullscreenHintHideRunnable)
        binding.fullscreenHint.text = msg
        binding.fullscreenHint.alpha = 1f
        binding.fullscreenHint.visibility = View.VISIBLE
        binding.fullscreenHint.postDelayed(fullscreenHintHideRunnable, 2500)
    }

    /** 전체화면 버튼 아이콘 갱신 */
    private fun updateFullscreenButton(fullscreen: Boolean) {
        binding.btnFullscreen.setImageResource(
            if (fullscreen) R.drawable.ic_fullscreen_exit else R.drawable.ic_fullscreen
        )
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
