package com.yangfei.smrtenglish.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.GridLayoutManager
import com.scwang.smart.refresh.header.ClassicsHeader
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.LibraryTypeCard
import com.yangfei.smrtenglish.databinding.ActivityCourseListBinding
import com.yangfei.smrtenglish.ui.library.adapter.CourseAdapter
import com.yangfei.smrtenglish.util.UiUtils.courseSpanCount

/**
 * 类型课程列表页（对齐 iOS CourseListScreen，?s={type}）
 */
class CourseListActivity : BaseActivity<ActivityCourseListBinding, CourseListViewModel>() {

    private lateinit var adapter: CourseAdapter

    private val typeCard: LibraryTypeCard by lazy {
        intent.getParcelableExtra(EXTRA_TYPE_CARD) ?: LibraryTypeCard()
    }

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar { it.setTitle(typeCard.name) }

        adapter = CourseAdapter { course ->
            startActivity(Intent(this, CourseDetailActivity::class.java).apply {
                putExtra(CourseDetailActivity.EXTRA_COURSE, course)
            })
        }
        // 列数自适应：紧凑 2 列，大屏 3 列（对齐 iOS CourseListScreen）
        binding.recyclerView.layoutManager = GridLayoutManager(this, courseSpanCount(this))
        binding.recyclerView.adapter = adapter

        binding.swipeRefresh.setRefreshHeader(ClassicsHeader(this))
        binding.swipeRefresh.setOnRefreshListener { viewModel.loadCourses(typeCard.key) }
        binding.btnRetry.setOnClickListener { loadData() }
    }

    override fun initObserver() {
        viewModel.coursesResult.observe(this) { response ->
            binding.swipeRefresh.finishRefresh()
            if (response.isSuccessful) {
                adapter.submitList(ArrayList(response.data ?: emptyList()))
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
            } else {
                showError(response.message ?: getString(R.string.error_bad_response))
            }
        }
    }

    override fun loadData() {
        binding.layoutLoading.isVisible = true
        binding.layoutError.isVisible = false
        viewModel.loadCourses(typeCard.key.ifEmpty { "youth" })
    }

    private fun showError(message: String) {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvError.text = message
    }

    companion object {
        const val EXTRA_TYPE_CARD = "extra_type_card"
    }
}
