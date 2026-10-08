package com.yangfei.smrtenglish.ui.cafe

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.scwang.smart.refresh.header.ClassicsHeader
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseFragment
import com.yangfei.smrtenglish.bean.CafeCategory
import com.yangfei.smrtenglish.bean.CafeTab
import com.yangfei.smrtenglish.databinding.FragmentCafeBinding
import com.yangfei.smrtenglish.ui.cafe.adapter.CafeVideoAdapter
import com.yangfei.smrtenglish.util.SimpleTextWatcher
import com.yangfei.smrtenglish.util.UiUtils.cafeSpanCount
import com.yangfei.smrtenglish.util.UiUtils.dp
import com.yangfei.smrtenglish.util.showToast

/**
 * Cafe 视频流页面（对齐 iOS CafeView）
 * - 顶部分级 Tab（All / Adults / Youth / Kids / Children）
 * - 搜索框常驻，回车后进入搜索 Tab（/cafe/all/search?s=关键词）
 * - 卡片分类标签可点击：分类名填入搜索框并切换到分类 Tab
 * - 卡片网格 + 滚动到底自动加载下一页（?page=N）；列数按屏幕宽度自适应（紧凑 2 列 / 大屏 4 列）
 */
class CafeFragment : BaseFragment<FragmentCafeBinding, CafeViewModel>() {

    private lateinit var adapter: CafeVideoAdapter

    private var currentPage = 1
    private var hasNextPage = true
    private var loadingPage = 1

    private val currentTab: CafeTab get() = viewModel.currentTab

    override fun initView(root: View, savedInstanceState: Bundle?) {
        adapter = CafeVideoAdapter(
            onVideoClick = { video ->
                startActivity(Intent(requireContext(), VideoPlayerActivity::class.java).apply {
                    putExtra(VideoPlayerActivity.EXTRA_VIDEO, video)
                })
            },
            onCategoryClick = { category -> selectCategory(category) }
        )

        binding.recyclerView.layoutManager = GridLayoutManager(requireContext(), cafeSpanCount(requireContext()))
        binding.recyclerView.adapter = adapter
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                if (dy <= 0) return
                val lm = recyclerView.layoutManager as? GridLayoutManager ?: return
                val lastVisible = lm.findLastVisibleItemPosition()
                val total = adapter.itemCount
                if (total > 0 && lastVisible >= total - 3) loadMore()
            }
        })

        binding.swipeRefresh.setRefreshHeader(ClassicsHeader(requireContext()))
        binding.swipeRefresh.setOnRefreshListener { loadPage(1, showDialog = false) }
        binding.btnRetry.setOnClickListener { loadPage(1) }

        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                commitSearch()
                true
            } else false
        }
        binding.tvSearchAction.setOnClickListener {
            if (binding.etSearch.text.isNullOrEmpty()) commitSearch() else resetFilter()
        }
        // 搜索框右侧按钮：有输入时显示 ✕（清空），无输入时显示「搜索」
        binding.etSearch.addTextChangedListener(SimpleTextWatcher { text ->
            binding.tvSearchAction.text =
                if (text.isEmpty()) getString(R.string.cafe_search) else "✕"
        })

        buildLevelTabs()
    }

    override fun initObserver() {
        viewModel.videosResult.observe(viewLifecycleOwner) { response ->
            val page = loadingPage
            binding.swipeRefresh.finishRefresh()
            if (response.isSuccessful) {
                val result = response.data
                if (result == null || result.videos.isEmpty()) {
                    if (page <= 1) showEmpty() else showToast(getString(R.string.no_more))
                    return@observe
                }
                if (page <= 1) {
                    adapter.submitList(ArrayList(result.videos))
                    binding.recyclerView.scrollToPosition(0)
                    currentPage = 1
                } else {
                    adapter.addAll(result.videos)
                    currentPage = page
                }
                hasNextPage = result.hasNext
                showContent()
            } else {
                if (page <= 1) {
                    showError(response.message ?: getString(R.string.error_bad_response))
                } else {
                    // 追加失败不打断现有列表，保留 hasNextPage 以便重试
                    loadingPage = currentPage
                    showToast(response.message)
                }
            }
        }
    }

    override fun loadData() {
        loadPage(1)
    }

    // MARK: - 分级 Tab

    private fun buildLevelTabs() {
        val container = binding.levelContainer
        container.removeAllViews()
        val chipViewsByPath = HashMap<String, TextView>()
        val tabs = CafeTab.levels.toMutableList()
        if (!CafeTab.isLevel(currentTab.path)) tabs.add(currentTab)

        tabs.forEach { tab ->
            val isLevel = CafeTab.isLevel(tab.path)
            val active = tab.path == currentTab.path
            val chip = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_level_chip, container, false) as TextView
            chip.text = if (isLevel) tab.title else "${tab.title}  ✕"
            chip.setTextColor(
                if (active) ContextCompat.getColor(requireContext(), R.color.white)
                else ContextCompat.getColor(requireContext(), R.color.text_secondary)
            )
            chip.background = ContextCompat.getDrawable(
                requireContext(),
                if (active) R.drawable.bg_tab_selected else R.drawable.bg_tab_normal
            )
            chip.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            chip.elevation = dp(requireContext(), 1f).toFloat()
            chip.setOnClickListener {
                if (tab.path == currentTab.path) {
                    // 动态 Tab 的 ✕ = 关闭该筛选：清空搜索框并回到 All
                    // （iOS 此处是装饰性图标、点了无反应，这里做了改进）
                    if (!isLevel) selectTab(CafeTab.all)
                } else {
                    selectTab(tab)
                }
            }
            container.addView(chip)
            if (active) chipViewsByPath[tab.path] = chip
        }

        // 选中 chip 滚动居中（对齐 iOS 5374d76：Tab 栏点击后滚动居中）
        val activeChip = chipViewsByPath[currentTab.path]
        if (activeChip != null) {
            val scroll = binding.levelScroll
            scroll.post {
                val target = activeChip.left + activeChip.width / 2 - scroll.width / 2
                scroll.smoothScrollTo(target.coerceAtLeast(0), 0)
            }
        }
    }

    private fun selectTab(tab: CafeTab) {
        viewModel.currentTab = tab
        // 搜索框文本只在「搜索/分类」动态 Tab 下有意义：
        // 切到任何分级 Tab（All / Adults / Youth / Kids / Children）时清空，
        // 避免出现「输入框还写着分类名、列表却已是全部分级」的不一致状态。
        if (CafeTab.isLevel(tab.path)) binding.etSearch.setText("")
        buildLevelTabs()
        loadPage(1)
    }

    /**
     * 复位筛选（点击搜索框的 ✕）：
     * 清空搜索框；若当前处在搜索/分类 Tab 则回到 All 并重新加载列表。
     * 已在分级 Tab 时只清空文本，不做无意义的重新请求。
     */
    private fun resetFilter() {
        if (CafeTab.isLevel(currentTab.path)) {
            binding.etSearch.setText("")
        } else {
            // selectTab 内部会一并清空搜索框
            selectTab(CafeTab.all)
        }
    }

    /** 提交搜索：关键词 → 搜索 Tab */
    private fun commitSearch() {
        val keyword = binding.etSearch.text.toString().trim()
        if (keyword.isEmpty()) return
        binding.etSearch.clearFocus()
        selectTab(CafeTab.search(keyword))
    }

    /** 点击分类标签：分类名填入搜索框 + 切到分类 Tab（与原版行为一致） */
    private fun selectCategory(category: CafeCategory) {
        binding.etSearch.setText(category.name)
        binding.etSearch.clearFocus()
        selectTab(CafeTab.category(category))
    }

    // MARK: - 数据

    private fun loadPage(page: Int, showDialog: Boolean = true) {
        loadingPage = page
        if (showDialog) showLoading()
        viewModel.loadVideos(page, showDialog)
    }

    private fun loadMore() {
        if (!hasNextPage) return
        if (loadingPage > currentPage) return
        loadPage(currentPage + 1, showDialog = false)
    }

    // MARK: - 状态切换

    private fun showLoading() {
        binding.layoutLoading.isVisible = true
        binding.layoutError.isVisible = false
        binding.layoutEmpty.isVisible = false
    }

    private fun showContent() {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = false
        binding.layoutEmpty.isVisible = false
        binding.swipeRefresh.isVisible = true
    }

    private fun showEmpty() {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = false
        binding.layoutEmpty.isVisible = true
    }

    private fun showError(message: String) {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.layoutEmpty.isVisible = false
        binding.tvError.text = message
    }
}
