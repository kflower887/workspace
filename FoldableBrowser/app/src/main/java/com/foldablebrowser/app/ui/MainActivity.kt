package com.foldablebrowser.app.ui

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
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
import androidx.constraintlayout.widget.ConstraintSet
import androidx.lifecycle.lifecycleScope
import com.foldablebrowser.app.R
import com.foldablebrowser.app.browser.BrowserViewModel
import com.foldablebrowser.app.browser.FoldableBrowserController
import com.foldablebrowser.app.browser.FoldableMode
import com.foldablebrowser.app.browser.SyncScrollWebView
import com.foldablebrowser.app.browser.TabItem
import com.foldablebrowser.app.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * 폴더블 스크롤 브라우저 메인 액티비티
 *
 * 주요 기능:
 * 1. 2단/3단 폴더블 분할 브라우저 (패널 간 연속 스크롤 동기화)
 * 2. 삼성 인터넷 스타일 UI (주소창, 탭 관리, 상하단 툴바)
 * 3. 모드 전환 버튼 (일반/2단/3단)
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
                    navigateToUrl("https://www.google.com")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        browserController = FoldableBrowserController(this)

        // 초기 탭 추가
        if (viewModel.getTabCount() == 0) {
            viewModel.addTab("https://www.google.com")
        }

        setupBrowserController()
        setupInitialMode()
        setupAddressBar()
        setupNavigationButtons()
        setupBottomBar()
        observeViewModel()

        // 초기 URL 로드
        navigateToUrl("https://www.google.com")
    }

    // -----------------------------------------------------------------------
    // 브라우저 컨트롤러 설정
    // -----------------------------------------------------------------------

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

                // 탭 정보 업데이트
                val tabs = viewModel.tabs.value ?: return@runOnUiThread
                val activeIdx = viewModel.activeTabIndex.value ?: 0
                tabs.getOrNull(activeIdx)?.url = url
                viewModel.tabs.value = tabs
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

    // -----------------------------------------------------------------------
    // 초기 모드 설정 (기본값: 2단 폴더블)
    // -----------------------------------------------------------------------

    private fun setupInitialMode() {
        applyMode(FoldableMode.DUAL)
    }

    /**
     * 폴더블 모드 적용 - 패널 재구성
     * @param mode 적용할 폴더블 모드
     */
    private fun applyMode(mode: FoldableMode) {
        viewModel.switchMode(mode)

        // 기존 WebView들 제거
        binding.webViewContainer.removeAllViews()

        // 컨트롤러에서 패널 WebView 목록 생성
        val panels = browserController.setupPanels(mode)

        when (mode) {
            FoldableMode.SINGLE -> {
                addSinglePanel(panels)
                updateModeButton(mode)
            }
            FoldableMode.DUAL -> {
                addDualPanels(panels)
                updateModeButton(mode)
            }
            FoldableMode.TRIPLE -> {
                addTriplePanels(panels)
                updateModeButton(mode)
            }
        }

        // 현재 URL 유지
        val currentUrl = viewModel.getActiveTab()?.url
        if (!currentUrl.isNullOrEmpty()) {
            browserController.loadUrl(currentUrl)
        }
    }

    /**
     * 단일 패널 (일반 모드)
     */
    private fun addSinglePanel(panels: List<SyncScrollWebView>) {
        val container = binding.webViewContainer
        container.orientation = LinearLayout.HORIZONTAL

        panels.firstOrNull()?.let { wv ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            container.addView(wv, params)
        }
    }

    /**
     * 2분할 패널 (2단 폴더블 모드)
     * 좌측: 패널 0 (마스터, 상단부)
     * 우측: 패널 1 (슬레이브, 하단부 자동 표시)
     */
    private fun addDualPanels(panels: List<SyncScrollWebView>) {
        val container = binding.webViewContainer
        container.orientation = LinearLayout.HORIZONTAL

        panels.forEachIndexed { index, wv ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )

            if (index == 0) {
                // 마스터 패널 (좌측)
                container.addView(wv, params)
                // 구분선 추가
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                container.addView(divider)
            } else {
                // 슬레이브 패널 (우측)
                container.addView(wv, params)
            }
        }
    }

    /**
     * 3분할 패널 (3단 폴더블 모드)
     * 패널 0 (마스터): 1번 구간
     * 패널 1 (슬레이브): 2번 구간
     * 패널 2 (슬레이브): 3번 구간
     */
    private fun addTriplePanels(panels: List<SyncScrollWebView>) {
        val container = binding.webViewContainer
        container.orientation = LinearLayout.HORIZONTAL

        panels.forEachIndexed { index, wv ->
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1f
            )
            container.addView(wv, params)

            // 패널 사이 구분선 (마지막 패널 제외)
            if (index < panels.size - 1) {
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                container.addView(divider)
            }
        }
    }

    // -----------------------------------------------------------------------
    // 주소창 설정
    // -----------------------------------------------------------------------

    private fun setupAddressBar() {
        binding.etAddressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                val input = binding.etAddressBar.text.toString().trim()
                if (input.isNotEmpty()) {
                    navigateToUrl(input)
                    hideKeyboard()
                }
                true
            } else false
        }

        binding.etAddressBar.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                // 포커스 시 전체 URL 표시
                binding.etAddressBar.setText(viewModel.currentUrl.value)
                binding.etAddressBar.selectAll()
            } else {
                // 포커스 해제 시 간략한 도메인 표시
                binding.etAddressBar.setText(
                    extractDisplayUrl(viewModel.currentUrl.value ?: "")
                )
            }
        }

        binding.btnRefresh.setOnClickListener {
            if (viewModel.isLoading.value == true) {
                browserController.stopLoading()
                binding.btnRefresh.setImageResource(R.drawable.ic_refresh)
            } else {
                browserController.reload()
            }
        }
    }

    // -----------------------------------------------------------------------
    // 상단 네비게이션 버튼 설정
    // -----------------------------------------------------------------------

    private fun setupNavigationButtons() {
        binding.btnBack.setOnClickListener {
            if (!browserController.goBack()) {
                Toast.makeText(this, "이전 페이지가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnForward.setOnClickListener {
            if (!browserController.goForward()) {
                Toast.makeText(this, "다음 페이지가 없습니다", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnTabCounter.setOnClickListener {
            openTabManager()
        }

        binding.btnMenu.setOnClickListener {
            showBrowserMenu()
        }
    }

    // -----------------------------------------------------------------------
    // 하단 바 설정
    // -----------------------------------------------------------------------

    private fun setupBottomBar() {
        binding.btnHome.setOnClickListener {
            navigateToUrl("https://www.google.com")
        }

        binding.btnBookmark.setOnClickListener {
            Toast.makeText(this, "북마크 기능 준비 중", Toast.LENGTH_SHORT).show()
        }

        // 폴더블 모드 전환 버튼
        binding.btnFoldableMode.setOnClickListener {
            showModeSelectionDialog()
        }

        binding.btnHistory.setOnClickListener {
            Toast.makeText(this, "히스토리 기능 준비 중", Toast.LENGTH_SHORT).show()
        }

        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "설정 기능 준비 중", Toast.LENGTH_SHORT).show()
        }
    }

    // -----------------------------------------------------------------------
    // 폴더블 모드 선택 다이얼로그
    // -----------------------------------------------------------------------

    /**
     * 모드 선택 다이얼로그 표시
     * 일반 / 2단 폴더블 / 3단 폴더블 선택
     */
    private fun showModeSelectionDialog() {
        val currentMode = viewModel.foldableMode.value ?: FoldableMode.DUAL
        val items = arrayOf(
            "📱 일반 모드  (단일 화면)",
            "📖 2단 폴더블  (좌우 2분할 연속 스크롤)",
            "📒 3단 폴더블  (3분할 연속 스크롤)"
        )
        val checkedItem = when (currentMode) {
            FoldableMode.SINGLE -> 0
            FoldableMode.DUAL -> 1
            FoldableMode.TRIPLE -> 2
        }

        AlertDialog.Builder(this)
            .setTitle("브라우저 분할 모드 선택")
            .setSingleChoiceItems(items, checkedItem) { dialog, which ->
                val newMode = when (which) {
                    0 -> FoldableMode.SINGLE
                    1 -> FoldableMode.DUAL
                    2 -> FoldableMode.TRIPLE
                    else -> FoldableMode.DUAL
                }
                applyMode(newMode)
                dialog.dismiss()

                val modeName = when (newMode) {
                    FoldableMode.SINGLE -> "일반 모드"
                    FoldableMode.DUAL -> "2단 폴더블 모드"
                    FoldableMode.TRIPLE -> "3단 폴더블 모드"
                }
                Toast.makeText(this, "$modeName 로 전환되었습니다", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("취소", null)
            .show()
    }

    /**
     * 모드 버튼 UI 업데이트
     */
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

    // -----------------------------------------------------------------------
    // 탭 매니저
    // -----------------------------------------------------------------------

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
        if (!url.isNullOrEmpty()) {
            browserController.loadUrl(url)
        }
    }

    // -----------------------------------------------------------------------
    // URL 네비게이션
    // -----------------------------------------------------------------------

    /**
     * URL 또는 검색어로 네비게이션
     * 검색어인 경우 Google 검색으로 전환
     */
    private fun navigateToUrl(input: String) {
        val url = when {
            input.startsWith("http://") || input.startsWith("https://") -> input
            input.contains(".") && !input.contains(" ") -> "https://$input"
            else -> "https://www.google.com/search?q=${android.net.Uri.encode(input)}"
        }

        // 탭 URL 업데이트
        val tabs = viewModel.tabs.value ?: return
        val activeIdx = viewModel.activeTabIndex.value ?: 0
        tabs.getOrNull(activeIdx)?.url = url
        viewModel.tabs.value = tabs

        browserController.loadUrl(url)
        binding.etAddressBar.setText(extractDisplayUrl(url))
    }

    /**
     * URL에서 표시용 도메인 추출
     */
    private fun extractDisplayUrl(url: String): String {
        return try {
            val uri = android.net.Uri.parse(url)
            uri.host?.removePrefix("www.") ?: url
        } catch (e: Exception) {
            url
        }
    }

    // -----------------------------------------------------------------------
    // ViewModel 관찰
    // -----------------------------------------------------------------------

    private fun observeViewModel() {
        viewModel.canGoBack.observe(this) { canGoBack ->
            binding.btnBack.alpha = if (canGoBack) 1.0f else 0.4f
            binding.btnBack.isEnabled = canGoBack
        }

        viewModel.canGoForward.observe(this) { canGoForward ->
            binding.btnForward.alpha = if (canGoForward) 1.0f else 0.4f
            binding.btnForward.isEnabled = canGoForward
        }

        viewModel.isLoading.observe(this) { loading ->
            binding.btnRefresh.setImageResource(
                if (loading) R.drawable.ic_close else R.drawable.ic_refresh
            )
        }

        viewModel.tabs.observe(this) {
            updateTabCounter()
        }
    }

    private fun updateTabCounter() {
        binding.tvTabCount.text = viewModel.getTabCount().toString()
    }

    // -----------------------------------------------------------------------
    // 브라우저 메뉴
    // -----------------------------------------------------------------------

    private fun showBrowserMenu() {
        val items = arrayOf(
            "새 탭",
            "현재 페이지 새로고침",
            "북마크에 추가",
            "페이지 공유",
            "PC 버전으로 보기"
        )

        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> {
                        viewModel.addTab()
                        navigateToUrl("https://www.google.com")
                    }
                    1 -> browserController.reload()
                    2 -> Toast.makeText(this, "북마크 추가 준비 중", Toast.LENGTH_SHORT).show()
                    3 -> {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, browserController.getCurrentUrl())
                        }
                        startActivity(Intent.createChooser(shareIntent, "페이지 공유"))
                    }
                    4 -> Toast.makeText(this, "PC 버전 준비 중", Toast.LENGTH_SHORT).show()
                }
            }
            .show()
    }

    // -----------------------------------------------------------------------
    // 시스템 이벤트
    // -----------------------------------------------------------------------

    override fun onBackPressed() {
        if (browserController.goBack()) {
            return
        }
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserController.destroy()
    }

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }
}
