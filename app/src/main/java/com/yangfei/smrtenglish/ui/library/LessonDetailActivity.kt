package com.yangfei.smrtenglish.ui.library

import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.databinding.ActivityLessonDetailBinding
import com.yangfei.smrtenglish.util.WebViewHelper
import com.yangfei.smrtenglish.util.showToast

/**
 * 课时详情页（对齐 iOS LessonDetailView）
 *
 * 整页 WebView 加载官网课时页 /sunshine/lesson/{courseId}/{lessonId}，
 * 课时切换、练习、视频等交互全部由官网页面自理。
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
        requireTitleBar { it.setTitle(lesson.title) }

        // 同步登录 Cookie：不同步会以游客态打开课时页
        WebViewHelper.syncCookies()
        WebViewHelper.configWebView(
            binding.webView,
            pageBackgroundColor = android.graphics.Color.WHITE,
            hideSiteHeader = true
        )

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.isVisible = false
                // 注入 CSS：隐藏官网 header + 修复课时页栅格布局
                view?.evaluateJavascript(WebViewHelper.hideHeaderJs(), null)
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }

        binding.webView.loadUrl(lessonUrl)
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
