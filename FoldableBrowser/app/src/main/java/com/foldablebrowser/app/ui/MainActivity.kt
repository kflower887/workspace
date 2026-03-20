package com.foldablebrowser.app.ui

import android.app.Activity
import android.content.Intent
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
import com.foldablebrowser.app.browser.BrowserViewModel
import com.foldablebrowser.app.browser.FoldableBrowserController
import com.foldablebrowser.app.browser.FoldableMode
import com.foldablebrowser.app.browser.SyncScrollWebView
import com.foldablebrowser.app.databinding.ActivityMainBinding

/**
 * 폴더블 스크롤 브라우저 메인 액티비티
 *
 * configChanges 처리 전략:
 * - Manifest에 orientation|screenSize 등을 선언해 Activity 재생성을 막음
 * - onConfigurationChanged()에서 패널 레이아웃만 재배치 (WebView 재생성 없음)
 *
 * 패널 방향 규칙:
 * - 가로 (width > height): 패널을 좌우 수평 배치 → 폴더블 펼친 상태
 * - 세로 (height > width): 패널을 상하 수직 배치 → 폴더블 세운 상태
 *
 * 스크롤 동기화는 방향 무관하게 동일 수식(scrollY 기반)으로 동작
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

    // -----------------------------------------------------------------------
    // 생명주기
    // -----------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        browserController = FoldableBrowserController(this)

        setupBrowserController()
        setupAddressBar()
        setupNavigationButtons()
        setupBottomBar()
        observeViewModel()

        if (savedInstanceState == null) {
            // 최초 실행: 탭 추가 후 홈 로드
            viewModel.addTab("https://www.google.com")
            val mode = viewModel.foldableMode.value ?: FoldableMode.DUAL
            applyMode(mode, loadUrl = true)
        } else {
            // 프로세스 복원: 패널만 재구성, URL 재로드 없음
            // (configChanges 선언으로 일반 회전은 이 경로 불통과)
            val mode = viewModel.foldableMode.value ?: FoldableMode.DUAL
            applyMode(mode, loadUrl = false)
            val url = viewModel.getActiveTab()?.url
            if (!url.isNullOrEmpty()) browserController.loadUrl(url)
        }
    }

    /**
     * Manifest configChanges 덕분에 회전 시 Activity 재생성 없이 여기로 진입.
     * WebView를 살린 채로 패널 레이아웃만 방향에 맞게 재배치한다.
     */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        rearrangePanels()
        adjustToolbarForOrientation(newConfig)
    }

    override fun onBackPressed() {
        if (browserController.goBack()) return
        super.onBackPressed()
    }

    override fun onDestroy() {
        super.onDestroy()
        browserController.destroy()
    }

    // -----------------------------------------------------------------------
    // 패널 구성 / 재배치
    // -----------------------------------------------------------------------

    /**
     * 모드 전환 또는 최초 초기화 시 호출.
     * WebView를 새로 생성하고 현재 방향에 맞게 배치한다.
     *
     * @param loadUrl true면 컨트롤러에 URL 로드 명령. 회전 시에는 false.
     */
    private fun applyMode(mode: FoldableMode, loadUrl: Boolean = true) {
        viewModel.switchMode(mode)
        binding.webViewContainer.removeAllViews()

        val panels = browserController.setupPanels(mode)
        arrangePanelsInContainer(panels, mode)
        updateModeButton(mode)

        if (loadUrl) {
            val url = viewModel.getActiveTab()?.url
            if (!url.isNullOrEmpty()) browserController.loadUrl(url)
        }
    }

    /**
     * 회전 전용: WebView 재생성 없이 기존 패널을 떼었다가 새 방향으로 재부착.
     */
    private fun rearrangePanels() {
        val panels = browserController.getPanels()
        if (panels.isEmpty()) return

        // 컨테이너에서 분리 (WebView 자체는 살아있음)
        binding.webViewContainer.removeAllViews()

        val mode = viewModel.foldableMode.value ?: FoldableMode.DUAL
        arrangePanelsInContainer(panels, mode)
    }

    /**
     * 패널 목록을 현재 화면 방향에 맞게 컨테이너에 배치.
     *
     * - 가로 화면(landscape / 펼친 폴더블): 패널을 좌→우 수평 배치
     * - 세로 화면(portrait):               패널을 위→아래 수직 배치
     *
     * 단일 패널(SINGLE)은 항상 전체 화면.
     */
    private fun arrangePanelsInContainer(
        panels: List<SyncScrollWebView>,
        mode: FoldableMode
    ) {
        if (panels.isEmpty()) return

        val container = binding.webViewContainer
        val landscape = isLandscape()

        if (mode == FoldableMode.SINGLE || panels.size == 1) {
            container.orientation = LinearLayout.HORIZONTAL
            val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            container.addView(panels[0], lp)
            return
        }

        // 가로: 수평 배치(좌우), 세로: 수직 배치(상하)
        container.orientation = if (landscape) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL

        panels.forEachIndexed { index, wv ->
            val lp = if (landscape) {
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
            } else {
                LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
            }
            container.addView(wv, lp)

            // 패널 사이 구분선 (마지막 패널 제외)
            if (index < panels.size - 1) {
                val divider = View(this).apply {
                    layoutParams = if (landscape) {
                        // 수평 배치 → 세로 구분선
                        LinearLayout.LayoutParams(2, LinearLayout.LayoutParams.MATCH_PARENT)
                    } else {
                        // 수직 배치 → 가로 구분선
                        LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
                    }
                    setBackgroundColor(getColor(R.color.divider_color))
                }
                container.addView(divider)
            }
        }
    }

    // -----------------------------------------------------------------------
    // 방향별 툴바 조정
    // -----------------------------------------------------------------------

    /**
     * 가로 모드에서 하단 바 높이를 줄여 웹 콘텐츠 영역을 최대화.
     * 세로 모드에서는 원래 높이(56dp)로 복원.
     */
    private fun adjustToolbarForOrientation(config: Configuration) {
        val landscape = config.orientation == Configuration.ORIENTATION_LANDSCAPE
        val bottomBarHeight = if (landscape) dpToPx(40) else dpToPx(56)
        val topBarHeight   = if (landscape) dpToPx(48) else dpToPx(56)

        binding.bottomNavBar.layoutParams =
            (binding.bottomNavBar.layoutParams).apply { height = bottomBarHeight }
        binding.topToolbar.layoutParams =
            (binding.topToolbar.layoutParams).apply { height = topBarHeight }

        binding.bottomNavBar.requestLayout()
        binding.topToolbar.requestLayout()
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density + 0.5f).toInt()

    private fun isLandscape(): Boolean {
        val dm = resources.displayMetrics
        return dm.widthPixels > dm.heightPixels
    }

    // -----------------------------------------------------------------------
    // 브라우저 컨트롤러 콜백 설정
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
    // 주소창
    // -----------------------------------------------------------------------

    private fun setupAddressBar() {
        binding.etAddressBar.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
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
                binding.etAddressBar.setText(viewModel.currentUrl.value)
                binding.etAddressBar.selectAll()
            } else {
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
    // 네비게이션 버튼
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
        binding.btnTabCounter.setOnClickListener { openTabManager() }
        binding.btnMenu.setOnClickListener { showBrowserMenu() }
    }

    // -----------------------------------------------------------------------
    // 하단 바
    // -----------------------------------------------------------------------

    private fun setupBottomBar() {
        binding.btnHome.setOnClickListener { navigateToUrl("https://www.google.com") }
        binding.btnBookmark.setOnClickListener {
            Toast.makeText(this, "북마크 기능 준비 중", Toast.LENGTH_SHORT).show()
        }
        binding.btnFoldableMode.setOnClickListener { showModeSelectionDialog() }
        binding.btnHistory.setOnClickListener {
            Toast.makeText(this, "히스토리 기능 준비 중", Toast.LENGTH_SHORT).show()
        }
        binding.btnSettings.setOnClickListener {
            Toast.makeText(this, "설정 기능 준비 중", Toast.LENGTH_SHORT).show()
        }
    }

    // -----------------------------------------------------------------------
    // 폴더블 모드 전환
    // -----------------------------------------------------------------------

    private fun showModeSelectionDialog() {
        val currentMode = viewModel.foldableMode.value ?: FoldableMode.DUAL
        val items = arrayOf(
            "📱 일반 모드  (단일 화면)",
            "📖 2단 폴더블  (좌우/상하 2분할 연속 스크롤)",
            "📒 3단 폴더블  (3분할 연속 스크롤)"
        )
        val checkedItem = when (currentMode) {
            FoldableMode.SINGLE -> 0
            FoldableMode.DUAL   -> 1
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
                applyMode(newMode, loadUrl = true)
                dialog.dismiss()
                val name = when (newMode) {
                    FoldableMode.SINGLE -> "일반 모드"
                    FoldableMode.DUAL   -> "2단 폴더블 모드"
                    FoldableMode.TRIPLE -> "3단 폴더블 모드"
                }
                Toast.makeText(this, "$name 로 전환되었습니다", Toast.LENGTH_SHORT).show()
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
        if (!url.isNullOrEmpty()) browserController.loadUrl(url)
    }

    // -----------------------------------------------------------------------
    // URL 네비게이션
    // -----------------------------------------------------------------------

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
    } catch (e: Exception) {
        url
    }

    // -----------------------------------------------------------------------
    // ViewModel 관찰
    // -----------------------------------------------------------------------

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
    }

    private fun updateTabCounter() {
        binding.tvTabCount.text = viewModel.getTabCount().toString()
    }

    // -----------------------------------------------------------------------
    // 브라우저 메뉴
    // -----------------------------------------------------------------------

    private fun showBrowserMenu() {
        val items = arrayOf("새 탭", "현재 페이지 새로고침", "북마크에 추가", "페이지 공유")
        AlertDialog.Builder(this)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> { viewModel.addTab(); navigateToUrl("https://www.google.com") }
                    1 -> browserController.reload()
                    2 -> Toast.makeText(this, "북마크 추가 준비 중", Toast.LENGTH_SHORT).show()
                    3 -> {
                        val share = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, browserController.getCurrentUrl())
                        }
                        startActivity(Intent.createChooser(share, "페이지 공유"))
                    }
                }
            }.show()
    }

    // -----------------------------------------------------------------------
    // 유틸
    // -----------------------------------------------------------------------

    private fun hideKeyboard() {
        val imm = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
        binding.etAddressBar.clearFocus()
    }
}
