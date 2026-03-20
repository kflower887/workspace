package com.foldablebrowser.app.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import com.foldablebrowser.app.R
import com.foldablebrowser.app.browser.TabItem
import com.foldablebrowser.app.databinding.ActivityTabManagerBinding
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * 탭 매니저 액티비티
 * 삼성 인터넷 스타일의 그리드형 탭 관리 화면
 */
class TabManagerActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_TABS = "extra_tabs"
        const val EXTRA_ACTIVE_TAB = "extra_active_tab"
        const val RESULT_TAB_SELECTED = "result_tab_selected"
        const val RESULT_TAB_CLOSED = "result_tab_closed"
        const val RESULT_NEW_TAB = "result_new_tab"
        const val EXTRA_RESULT_INDEX = "extra_result_index"
        const val EXTRA_RESULT_ACTION = "extra_result_action"
    }

    private lateinit var binding: ActivityTabManagerBinding
    private lateinit var tabAdapter: TabAdapter
    private var tabs = mutableListOf<TabItem>()
    private var activeTabIndex = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTabManagerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 인텐트에서 탭 데이터 복원
        @Suppress("UNCHECKED_CAST")
        tabs = (intent.getSerializableExtra(EXTRA_TABS) as? ArrayList<TabItem>)?.toMutableList()
            ?: mutableListOf()
        activeTabIndex = intent.getIntExtra(EXTRA_ACTIVE_TAB, 0)

        setupRecyclerView()
        setupClickListeners()
    }

    private fun setupRecyclerView() {
        tabAdapter = TabAdapter(
            tabs = tabs,
            onTabClick = { index ->
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_RESULT_ACTION, RESULT_TAB_SELECTED)
                    putExtra(EXTRA_RESULT_INDEX, index)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            },
            onTabClose = { index ->
                if (tabs.size == 1) {
                    // 마지막 탭은 닫지 않고 새 탭으로 대체
                    val resultIntent = Intent().apply {
                        putExtra(EXTRA_RESULT_ACTION, RESULT_NEW_TAB)
                        putExtra(EXTRA_RESULT_INDEX, 0)
                    }
                    setResult(Activity.RESULT_OK, resultIntent)
                    finish()
                    return@TabAdapter
                }
                val resultIntent = Intent().apply {
                    putExtra(EXTRA_RESULT_ACTION, RESULT_TAB_CLOSED)
                    putExtra(EXTRA_RESULT_INDEX, index)
                }
                setResult(Activity.RESULT_OK, resultIntent)
                finish()
            }
        )

        // 폴더블 화면 너비에 따라 컬럼 수 자동 조정
        val spanCount = if (resources.displayMetrics.widthPixels > 1200) 3 else 2
        binding.rvTabs.apply {
            layoutManager = GridLayoutManager(this@TabManagerActivity, spanCount)
            adapter = tabAdapter
        }
    }

    private fun setupClickListeners() {
        binding.btnCloseTabManager.setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        binding.fabNewTab.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra(EXTRA_RESULT_ACTION, RESULT_NEW_TAB)
                putExtra(EXTRA_RESULT_INDEX, tabs.size)
            }
            setResult(Activity.RESULT_OK, resultIntent)
            finish()
        }
    }
}
