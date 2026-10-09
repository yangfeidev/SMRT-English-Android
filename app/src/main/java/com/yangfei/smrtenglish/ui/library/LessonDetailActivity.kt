package com.yangfei.smrtenglish.ui.library

import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.databinding.ActivityLessonDetailBinding
import com.yangfei.smrtenglish.util.WebViewHelper

/**
 * 课时详情页（对齐 iOS LessonDetailView → WebViewScreen）
 *
 * 整页 WebView 加载官网课时页 /sunshine/lesson/{courseId}/{lessonId}，
 * 课时切换、练习、视频等交互全部由官网页面自理。
 * 返回按钮：WebView 有内部浏览历史先 goBack 逐级返回，无历史才退出页面；
 * 顶部细进度条随 onProgressChanged 伸缩（对齐 iOS WebViewProgressOverlay）。
 */
class LessonDetailActivity : BaseActivity<ActivityLessonDetailBinding, CourseDetailViewModel>() {

    private val course: LibraryCourse by lazy {
        intent.getParcelableExtra(EXTRA_COURSE) ?: LibraryCourse()
    }

    private val lesson: LessonItem by lazy {
        intent.getParcelableExtra(EXTRA_LESSON) ?: LessonItem()
    }

    private val lessonUrl: String
        get() = UrlConstant.fullUrl(UrlConstant.lesson(lesson.courseId, lesson.id))

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar {
            it.setTitle(lesson.title)
            it.setOnBackClickListener { handleBack() }
        }
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        // 同步登录 Cookie：不同步会以游客态打开课时页
        WebViewHelper.setupAndLoad(
            binding.webView,
            WebViewHelper.PageConfig(
                url = lessonUrl,
                syncCookies = true,
                pageBackgroundColor = Color.WHITE,
                hideSiteHeader = true
            ),
            WebViewHelper.bindProgress(binding.progressBar)
        )
    }

    /** 返回按钮：WebView 有内部浏览历史先 goBack，无历史退出页面 */
    private fun handleBack() {
        if (binding.webView.canGoBack()) {
            binding.webView.goBack()
        } else {
            finish()
        }
    }

    override fun initObserver() {}

    override fun loadData() {}

    override fun onPause() {
        super.onPause()
        binding.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        binding.webView.onResume()
    }

    override fun onDestroy() {
        binding.webView.stopLoading()
        binding.webView.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_COURSE = "extra_course"
        const val EXTRA_LESSON = "extra_lesson"
    }
}
