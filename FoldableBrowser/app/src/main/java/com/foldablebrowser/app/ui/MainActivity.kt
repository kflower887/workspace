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

/**
 * 폴더블 스크롤 브라우저 메인 액티비티 v3
 *
 * ■ 패널 배치: 항상 가로(좌/우) — 세로/가로 관계없이 HORIZONTAL
 * ■ 스크롤 연동 버튼 (lockOffset 방식)
 *     OFF(기본): 각 패널 완전 독립. 사용자가 원하는 위치로 각각 이동.
 *     ON 누름:  그 순간 각 패널의 위치 차이를 offset으로 확정(lock)
 *               이후 마스터 스크롤 → 슬레이브는 고정 offset 만큼 뒤따름
 *     새 URL 로드 / reload 시 자동으로 연동 해제
 * ■ 화면 회전 버튼: 세로↔가로 강제 전환
 * ■ 히스토리/북마크/설정: 하단 바 버튼에서 실제 동작
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val viewModel: BrowserViewModel by viewModels()
    private lateinit var browserController: FoldableBrowserController

    private val tabManagerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val action = data.getStringExtra(TabManagerActivity.EXTRA_RESULT_ACTION)
            val index = data.getIntExtra(TabManagerActivity.EXTRA_RESULT_INDEX, 0)
            when (action) {
                TabManagerActivity.RESULT_TAB_SELECTED -> {
                    viewModel.switchTab(index)
                    loadActiveTabUrl()
                }
                TabManagerActivity.RESULT_TAB_CLOSED -> {
                    viewModel.closeTab(index)
                    loadActiveTabUrl()
                }
                TabManagerActivity.RESULT_NEW_TAB -> {
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
        // 패널 배치는 항상 HORIZONTAL → 회전해도 재배치만
        rearrangePanels()
    }

    @Suppress("OVERRIDE_DEPRECATION")
    override fun onBackPressed() {
        if (browserController.goBack()) return
        @Suppress("DEPRECATION")
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserController.destroy()
    }

    // ─────────────────────────────────────────────────────────────
    // 패널 구성 — 항상 HORIZONTAL (좌/우)
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

    /** 회전 시: WebView 재생성 없이 기존 패널 재배치 */
    private fun rearrangePanels() {
        val panels = browserController.getPanels()
        if (panels.isEmpty()) return
        binding.webViewContainer.removeAllViews()
        arrangePanels(panels)
    }

    /**
     * 항상 가로(LEFT→RIGHT) 배치.
     * 세로 화면이든 가로 화면이든 패널은 좌/우로 나란히.
     */
    private fun arrangePanels(panels: List<SyncScrollWebView>) {
        if (panels.isEmpty()) return
        val container = binding.webViewContainer
        container.orientation = LinearLayout.HORIZONTAL   // 항상 가로

        if (panels.size == 1) {
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            container.addView(panels[0], lp)
            return
        }

        panels.forEachIndexed { index, wv ->
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            container.addView(wv, lp)

            if (index < panels.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                container.addView(divider)
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 상단 컨트롤바 (연동 잠금 버튼 + 화면 회전 버튼)
    // ─────────────────────────────────────────────────────────────

    private fun setupTopControls() {
        // ── 연동 버튼 ──────────────────────────────────────────────
        binding.btnSyncScroll.setOnClickListener {
            val mode = viewModel.foldableMode.value ?: FoldableMode.SINGLE
            if (mode == FoldableMode.SINGLE) {
                Toast.makeText(this, "분할 모드에서만 사용할 수 있습니다", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (browserController.isSyncActive) {
                // ── 연동 해제 ──
                browserController.unlockSync()
                viewModel.syncScrollEnabled.value = false
                updateSyncButton(false)
                Toast.makeText(
                    this,
                    "연동 해제 — 각 화면을 자유롭게 스크롤하세요",
                    Toast.LENGTH_SHORT
                ).show()
            } else {
                // ── 연동 잠금 ──
                // 현재 각 패널의 위치로 offset 확정
                val offsets = browserController.lockSyncFromCurrentPositions()
                viewModel.syncScrollEnabled.value = true
                updateSyncButton(true)

                // 사용자에게 확정된 오프셋 안내
                val offsetDesc = offsets.drop(1).mapIndexed { idx, off ->
                    val panel = if (idx == 0) "우측" else "${idx + 2}번째"
                    val dir = when {
                        off > 0 -> "${off}px 아래"
                        off < 0 -> "${-off}px 위"
                        else -> "동일 위치"
                    }
                    "$panel: $dir"
                }.joinToString(", ")

                Toast.makeText(
                    this,
                    "연동 잠금!\n좌측 스크롤 시 우측이 현재 위치 차이를 유지하며 따라갑니다.\n($offsetDesc)",
                    Toast.LENGTH_LONG
                ).show()
            }
        }

        // ── 화면 회전 버튼 ──────────────────────────────────────────
        binding.btnRotate.setOnClickListener {
            val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
            if (isLandscape) {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                viewModel.forceLandscape.value = false
            } else {
                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                viewModel.forceLandscape.value = true
            }
        }

        // 초기 상태 반영
        updateSyncButton(browserController.isSyncActive)
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
    // 브라우저 컨트롤러 콜백
    // ─────────────────────────────────────────────────────────────

    private fun setupBrowserController() {
        browserController.onPageStarted = { url ->
            runOnUiThread {
                binding.progressBar.visibility = View.VISIBLE
                viewModel.isLoading.value = true
                viewModel.currentUrl.value = url
                binding.etAddressBar.setText(url)
                viewModel.canGoBack.value = browserController.canGoBack()
                viewModel.canGoForward.value = browserController.canGoForward()
            }
        }

        browserController.onPageFinished = { url ->
            runOnUiThread {
                binding.progressBar.visibility = View.GONE
                viewModel.isLoading.value = false
                viewModel.currentUrl.value = url
                binding.etAddressBar.setText(extractDisplayUrl(url))
                viewModel.canGoBack.value = browserController.canGoBack()
                viewModel.canGoForward.value = browserController.canGoForward()

                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                val activeIdx = viewModel.activeTabIndex.value ?: 0
                tabs.getOrNull(activeIdx)?.url = url
                viewModel.tabs.value = tabs

                // 새 페이지 로드 완료 → 연동 버튼 상태 업데이트 (controller가 자동 해제함)
                updateSyncButton(browserController.isSyncActive)

                // 히스토리 자동 저장
                val title = browserController.getTitle().ifBlank { url }
                viewModel.addHistory(title, url)

                // 북마크 상태 업데이트
                updateBookmarkIcon(url)
            }
        }

        browserController.onTitleReceived = { title ->
            runOnUiThread {
                viewModel.pageTitle.value = title
                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                val activeIdx = viewModel.activeTabIndex.value ?: 0
                tabs.getOrNull(activeIdx)?.title = title
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
                val activeIdx = viewModel.activeTabIndex.value ?: 0
                tabs.getOrNull(activeIdx)?.favicon = icon
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
                browserController.stopLoading()
            } else {
                browserController.reload()
            }
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
        binding.btnFoldableMode.setOnClickListener { showModeSelectionDialog() }
        binding.btnHistory.setOnClickListener { showHistoryDialog() }
        binding.btnSettings.setOnClickListener { showSettingsDialog() }
    }

    // ─────────────────────────────────────────────────────────────
    // 폴더블 모드 선택
    // ─────────────────────────────────────────────────────────────

    private fun showModeSelectionDialog() {
        val currentMode = viewModel.foldableMode.value ?: FoldableMode.DUAL
        val items = arrayOf(
            "📱 일반 모드  (단일 화면)",
            "📖 2단 분할  (좌/우 연속 스크롤)",
            "📒 3단 분할  (좌/중/우 연속 스크롤)"
        )
        val checkedItem = when (currentMode) {
            FoldableMode.SINGLE -> 0
            FoldableMode.DUAL -> 1
            FoldableMode.TRIPLE -> 2
        }
        AlertDialog.Builder(this)
            .setTitle("브라우저 분할 모드 선택")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val newMode = when (which) { 0 -> FoldableMode.SINGLE; 1 -> FoldableMode.DUAL; else -> FoldableMode.TRIPLE }
                applyMode(newMode, loadUrl = true)
                dialog.dismiss()
                Toast.makeText(this, "${items[which].drop(3)} 로 전환", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateModeButton(mode: FoldableMode) {
        when (mode) {
            FoldableMode.SINGLE -> {
                binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_single)
                binding.tvFoldableMode.text = "일반"
            }
            FoldableMode.DUAL -> {
                binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_dual)
                binding.tvFoldableMode.text = "2단"
            }
            FoldableMode.TRIPLE -> {
                binding.ivFoldableMode.setImageResource(R.drawable.ic_foldable_triple)
                binding.tvFoldableMode.text = "3단"
            }
        }
    }

    // ─────────────────────────────────────────────────────────────
    // 히스토리 다이얼로그
    // ─────────────────────────────────────────────────────────────

    private fun showHistoryDialog() {
        val list = viewModel.historyList.value ?: mutableListOf()
        if (list.isEmpty()) {
            Toast.makeText(this, "방문 기록이 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = list.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("방문 기록")
            .setItems(titles) { _, which ->
                navigateToUrl(list[which].url)
            }
            .setNeutralButton("전체 삭제") { _, _ ->
                AlertDialog.Builder(this)
                    .setMessage("방문 기록을 모두 삭제하시겠습니까?")
                    .setPositiveButton("삭제") { _, _ ->
                        viewModel.clearHistory()
                        Toast.makeText(this, "방문 기록 삭제 완료", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("취소", null)
                    .show()
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    // ─────────────────────────────────────────────────────────────
    // 북마크 다이얼로그
    // ─────────────────────────────────────────────────────────────

    private fun showBookmarkDialog() {
        val currentUrl = viewModel.currentUrl.value ?: ""
        val isBookmarked = viewModel.isBookmarked(currentUrl)

        val options = mutableListOf<String>()
        if (currentUrl.isNotBlank()) {
            options.add(if (isBookmarked) "★ 북마크 제거" else "☆ 현재 페이지 북마크 추가")
        }
        options.add("북마크 목록 보기")

        AlertDialog.Builder(this)
            .setTitle("북마크")
            .setItems(options.toTypedArray()) { _, which ->
                when {
                    which == 0 && currentUrl.isNotBlank() -> {
                        if (isBookmarked) {
                            val id = viewModel.bookmarkList.value?.find { it.url == currentUrl }?.id ?: return@setItems
                            viewModel.removeBookmark(id)
                            Toast.makeText(this, "북마크 제거됨", Toast.LENGTH_SHORT).show()
                        } else {
                            val title = browserController.getTitle().ifBlank { currentUrl }
                            viewModel.addBookmark(title, currentUrl)
                            Toast.makeText(this, "북마크 추가됨", Toast.LENGTH_SHORT).show()
                        }
                        updateBookmarkIcon(currentUrl)
                    }
                    else -> showBookmarkList()
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showBookmarkList() {
        val list = viewModel.bookmarkList.value ?: mutableListOf()
        if (list.isEmpty()) {
            Toast.makeText(this, "저장된 북마크가 없습니다", Toast.LENGTH_SHORT).show()
            return
        }
        val titles = list.map { "${it.title}\n${it.url}" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("북마크 목록")
            .setItems(titles) { _, which -> navigateToUrl(list[which].url) }
            .setNeutralButton("편집") { _, _ -> showBookmarkDeleteDialog() }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showBookmarkDeleteDialog() {
        val list = viewModel.bookmarkList.value ?: return
        val titles = list.map { it.title }.toTypedArray()
        val checked = BooleanArray(list.size) { false }
        AlertDialog.Builder(this)
            .setTitle("삭제할 북마크 선택")
            .setMultiChoiceItems(titles, checked) { _, idx, isChecked -> checked[idx] = isChecked }
            .setPositiveButton("삭제") { _, _ ->
                list.filterIndexed { idx, _ -> checked[idx] }
                    .forEach { viewModel.removeBookmark(it.id) }
                Toast.makeText(this, "선택 북마크 삭제됨", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun updateBookmarkIcon(url: String) {
        val starred = viewModel.isBookmarked(url)
        binding.btnBookmark.setImageResource(
            if (starred) R.drawable.ic_bookmark_filled else R.drawable.ic_bookmark
        )
    }

    // ─────────────────────────────────────────────────────────────
    // 설정 다이얼로그
    // ─────────────────────────────────────────────────────────────

    private fun showSettingsDialog() {
        val cur = viewModel.settings.value ?: BrowserSettings()
        val items = arrayOf(
            "홈페이지: ${cur.homePage}",
            "텍스트 크기: ${cur.textSize}%",
            "자바스크립트: ${if (cur.jsEnabled) "허용" else "차단"}",
            "데스크톱 모드: ${if (cur.desktopMode) "켜짐" else "꺼짐"}"
        )
        AlertDialog.Builder(this)
            .setTitle("설정")
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showHomePageSetting(cur)
                    1 -> showTextSizeSetting(cur)
                    2 -> {
                        val newSettings = cur.copy(jsEnabled = !cur.jsEnabled)
                        viewModel.updateSettings(newSettings)
                        applySettingsToBrowser(newSettings)
                        showSettingsDialog()
                    }
                    3 -> {
                        val newSettings = cur.copy(desktopMode = !cur.desktopMode)
                        viewModel.updateSettings(newSettings)
                        applySettingsToBrowser(newSettings)
                        showSettingsDialog()
                    }
                }
            }
            .setNegativeButton("닫기", null)
            .show()
    }

    private fun showHomePageSetting(cur: BrowserSettings) {
        val editText = android.widget.EditText(this).apply {
            setText(cur.homePage)
            hint = "https://www.google.com"
        }
        AlertDialog.Builder(this)
            .setTitle("홈페이지 설정")
            .setView(editText)
            .setPositiveButton("저장") { _, _ ->
                val url = editText.text.toString().trim()
                if (url.isNotEmpty()) {
                    val newUrl = if (url.startsWith("http")) url else "https://$url"
                    viewModel.updateSettings(cur.copy(homePage = newUrl))
                    Toast.makeText(this, "홈페이지 저장됨", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("취소", null)
            .show()
    }

    private fun showTextSizeSetting(cur: BrowserSettings) {
        val sizes = arrayOf("75% (작게)", "90%", "100% (보통)", "110%", "125%", "150% (크게)")
        val sizeValues = intArrayOf(75, 90, 100, 110, 125, 150)
        val currentIdx = sizeValues.indexOfFirst { it == cur.textSize }.takeIf { it >= 0 } ?: 2
        AlertDialog.Builder(this)
            .setTitle("텍스트 크기")
            .setSingleChoiceItems(sizes, currentIdx) { dialog, which ->
                val newSettings = cur.copy(textSize = sizeValues[which])
                viewModel.updateSettings(newSettings)
                applySettingsToBrowser(newSettings)
                dialog.dismiss()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    @Suppress("UNUSED_PARAMETER")
    private fun applySettingsToBrowser(newSettings: BrowserSettings) {
        // 설정 저장 완료 — 실제 WebView 반영은 다음 페이지 로드 시
        Toast.makeText(this, "설정이 저장되었습니다\n(다음 페이지부터 적용)", Toast.LENGTH_SHORT).show()
    }

    // ─────────────────────────────────────────────────────────────
    // 탭 매니저
    // ─────────────────────────────────────────────────────────────

    private fun openTabManager() {
        val tabs = viewModel.tabs.value ?: return
        val intent = Intent(this, TabManagerActivity::class.java).apply {
            putExtra(TabManagerActivity.EXTRA_TABS, ArrayList(tabs))
            putExtra(TabManagerActivity.EXTRA_ACTIVE_TAB, viewModel.activeTabIndex.value ?: 0)
        }
        tabManagerLauncher.launch(intent)
    }

    private fun loadActiveTabUrl() {
        val url = viewModel.getActiveTab()?.url
        if (!url.isNullOrEmpty()) browserController.loadUrl(url)
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
        val activeIdx = viewModel.activeTabIndex.value ?: 0
        tabs.getOrNull(activeIdx)?.url = url
        viewModel.tabs.value = tabs
        browserController.loadUrl(url)
        binding.etAddressBar.setText(extractDisplayUrl(url))
    }

    private fun extractDisplayUrl(url: String): String = try {
        android.net.Uri.parse(url).host?.removePrefix("www.") ?: url
    } catch (e: Exception) { url }

    // ─────────────────────────────────────────────────────────────
    // ViewModel 관찰
    // ─────────────────────────────────────────────────────────────

    private fun observeViewModel() {
        viewModel.canGoBack.observe(this) { can ->
            binding.btnBack.alpha = if (can) 1f else 0.4f
            binding.btnBack.isEnabled = can
        }
        viewModel.canGoForward.observe(this) { can ->
            binding.btnForward.alpha = if (can) 1f else 0.4f
            binding.btnForward.isEnabled = can
        }
        viewModel.isLoading.observe(this) { loading ->
            binding.btnRefresh.setImageResource(
                if (loading) R.drawable.ic_close else R.drawable.ic_refresh
            )
        }
        viewModel.tabs.observe(this) { updateTabCounter() }
        // syncScrollEnabled 는 controller 상태에서 직접 읽으므로 observe 불필요
        // (버튼 상태는 setupTopControls / onPageFinished 에서 갱신)
    }

    private fun updateTabCounter() {
        binding.tvTabCount.text = viewModel.getTabCount().toString()
    }

    // ─────────────────────────────────────────────────────────────
    // 브라우저 메뉴
    // ─────────────────────────────────────────────────────────────

    private fun showBrowserMenu() {
        val items = arrayOf("새 탭", "새로고침", "북마크에 추가", "페이지 공유", "히스토리", "설정")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { viewModel.addTab(); navigateToUrl(viewModel.settings.value?.homePage ?: "https://www.google.com") }
                    1 -> browserController.reload()
                    2 -> {
                        val url = viewModel.currentUrl.value ?: ""
                        val title = browserController.getTitle()
                        viewModel.addBookmark(title, url)
                        updateBookmarkIcon(url)
                        Toast.makeText(this, "북마크 추가됨", Toast.LENGTH_SHORT).show()
                    }
                    3 -> {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, browserController.getCurrentUrl())
                        }
                        startActivity(Intent.createChooser(share, "페이지 공유"))
                    }
                    4 -> showHistoryDialog()
                    5 -> showSettingsDialog()
                }
            }.show()
    }

    // ─────────────────────────────────────────────────────────────
    // 유틸
    // ─────────────────────────────────────────────────────────────

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }
}
