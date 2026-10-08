package com.yangfei.smrtenglish.ui.library

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.databinding.ActivityCourseDetailBinding
import com.yangfei.smrtenglish.ui.library.adapter.CourseDetailAdapter

/**
 * 课程详情页（对齐 iOS CourseDetailView）
 * 顶部课程封面横幅 + 单元树（单元头 → 分组 → 课时条目）
 */
class CourseDetailActivity : BaseActivity<ActivityCourseDetailBinding, CourseDetailViewModel>() {

    private lateinit var adapter: CourseDetailAdapter

    private val course: LibraryCourse by lazy {
        intent.getParcelableExtra(EXTRA_COURSE) ?: LibraryCourse()
    }

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar { it.setTitle(course.title.ifEmpty { getString(R.string.library_title) }) }

        adapter = CourseDetailAdapter(course) { lesson -> openLesson(lesson) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        binding.btnRetry.setOnClickListener { loadData() }
    }

    override fun initObserver() {
        viewModel.unitsResult.observe(this) { response ->
            if (response.isSuccessful) {
                adapter.submit(response.data ?: emptyList())
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
        viewModel.loadUnits(course.id)
    }

    private fun openLesson(lesson: LessonItem) {
        startActivity(Intent(this, LessonDetailActivity::class.java).apply {
            putExtra(LessonDetailActivity.EXTRA_COURSE, course)
            putExtra(LessonDetailActivity.EXTRA_LESSON, lesson)
        })
    }

    private fun showError(message: String) {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvError.text = message
    }

    companion object {
        const val EXTRA_COURSE = "extra_course"
    }
}
