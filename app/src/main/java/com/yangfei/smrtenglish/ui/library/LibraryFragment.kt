package com.yangfei.smrtenglish.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.core.view.isVisible
import com.scwang.smart.refresh.header.ClassicsHeader
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseFragment
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.bean.LibraryHome
import com.yangfei.smrtenglish.databinding.FragmentLibraryBinding
import com.yangfei.smrtenglish.databinding.ItemLibraryCoverBinding
import com.yangfei.smrtenglish.databinding.ItemLibrarySectionBinding
import com.yangfei.smrtenglish.databinding.ItemLibraryTypeBinding
import com.yangfei.smrtenglish.util.UiUtils.coverCardHeight
import com.yangfei.smrtenglish.util.UiUtils.coverCardWidth
import com.yangfei.smrtenglish.util.UiUtils.dp
import com.yangfei.smrtenglish.util.UiUtils.typeCardHeight
import com.yangfei.smrtenglish.util.UiUtils.typeCardWidth
import com.yangfei.smrtenglish.util.loadUrl

/**
 * Library 首页（对齐 iOS LibraryView，严格按官网 /sunshine/library 纵向结构）
 * - 顶部标题 Courses
 * - "English Courses" 分区：横滑类型卡
 * - 各类型分组分区：标题 + 横滑课程封面卡
 */
class LibraryFragment : BaseFragment<FragmentLibraryBinding, LibraryViewModel>() {

    override fun initView(root: View, savedInstanceState: Bundle?) {
        binding.swipeRefresh.setRefreshHeader(ClassicsHeader(requireContext()))
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadHome(false) }
        binding.btnRetry.setOnClickListener { viewModel.loadHome(false) }
    }

    override fun initObserver() {
        viewModel.homeResult.observe(viewLifecycleOwner) { response ->
            binding.swipeRefresh.finishRefresh()
            if (response.isSuccessful) {
                val home = response.data
                if (home == null) {
                    showError(getString(R.string.error_parse_failed))
                } else {
                    render(home)
                    binding.layoutLoading.isVisible = false
                    binding.layoutError.isVisible = false
                }
            } else {
                showError(response.message ?: getString(R.string.error_bad_response))
            }
        }
    }

    override fun loadData() {
        binding.layoutLoading.isVisible = true
        binding.layoutError.isVisible = false
        viewModel.loadHome(false)
    }

    // MARK: - 渲染

    private fun render(home: LibraryHome) {
        val container = binding.contentContainer
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (home.types.isNotEmpty()) {
            val section = ItemLibrarySectionBinding.inflate(inflater, container, false)
            section.tvSectionTitle.setText(R.string.library_types)
            home.types.forEach { type ->
                val item = ItemLibraryTypeBinding.inflate(inflater, section.rowContainer, false)
                item.tvName.text = type.name
                item.ivImage.loadUrl(type.imageURL)
                // 卡尺寸按屏幕自适应（紧凑 250×170 / 大屏 320×210）
                item.root.layoutParams.apply {
                    width = dp(requireContext(), typeCardWidth(requireContext()).toFloat())
                    height = dp(requireContext(), typeCardHeight(requireContext()).toFloat())
                }
                item.root.setOnClickListener {
                    startActivity(Intent(requireContext(), CourseListActivity::class.java).apply {
                        putExtra(CourseListActivity.EXTRA_TYPE_CARD, type)
                    })
                }
                section.rowContainer.addView(item.root)
            }
            container.addView(section.root)
        }

        home.groups.forEach { group ->
            val section = ItemLibrarySectionBinding.inflate(inflater, container, false)
            section.tvSectionTitle.text = group.title
            group.courses.forEach { card ->
                val item = ItemLibraryCoverBinding.inflate(inflater, section.rowContainer, false)
                item.ivImage.loadUrl(card.imageURL)
                // 封面卡尺寸按屏幕自适应（紧凑 150×195 / 大屏 200×250）
                item.root.layoutParams.apply {
                    width = dp(requireContext(), coverCardWidth(requireContext()).toFloat())
                    height = dp(requireContext(), coverCardHeight(requireContext()).toFloat())
                }
                item.root.setOnClickListener {
                    startActivity(Intent(requireContext(), CourseDetailActivity::class.java).apply {
                        putExtra(
                            CourseDetailActivity.EXTRA_COURSE,
                            LibraryCourse(id = card.id, imageURL = card.imageURL)
                        )
                    })
                }
                section.rowContainer.addView(item.root)
            }
            container.addView(section.root)
        }
    }

    private fun showError(message: String) {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvError.text = message
    }
}
