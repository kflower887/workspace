package com.foldablebrowser.app.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.foldablebrowser.app.R
import com.foldablebrowser.app.browser.TabItem

/**
 * 탭 매니저 RecyclerView 어댑터
 * 삼성 인터넷 스타일의 카드형 탭 목록 표시
 */
class TabAdapter(
    private var tabs: List<TabItem>,
    private val onTabClick: (Int) -> Unit,
    private val onTabClose: (Int) -> Unit
) : RecyclerView.Adapter<TabAdapter.TabViewHolder>() {

    inner class TabViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        val tvUrl: TextView = view.findViewById(R.id.tvUrl)
        val ivFavicon: ImageView = view.findViewById(R.id.ivFavicon)
        val btnClose: ImageButton = view.findViewById(R.id.btnCloseTab)
        val activeIndicator: View = view.findViewById(R.id.activeIndicator)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TabViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_tab, parent, false)
        return TabViewHolder(view)
    }

    override fun onBindViewHolder(holder: TabViewHolder, position: Int) {
        val tab = tabs[position]

        holder.tvTitle.text = tab.title.ifEmpty { "새 탭" }
        holder.tvUrl.text = tab.url.ifEmpty { "새 탭" }

        // 파비콘 설정
        if (tab.favicon != null) {
            holder.ivFavicon.setImageBitmap(tab.favicon)
        } else {
            holder.ivFavicon.setImageResource(R.drawable.ic_web)
        }

        // 활성 탭 표시
        holder.activeIndicator.visibility = if (tab.isActive) View.VISIBLE else View.GONE

        holder.itemView.setOnClickListener { onTabClick(position) }
        holder.btnClose.setOnClickListener { onTabClose(position) }
    }

    override fun getItemCount() = tabs.size

    fun updateTabs(newTabs: List<TabItem>) {
        tabs = newTabs
        notifyDataSetChanged()
    }
}
