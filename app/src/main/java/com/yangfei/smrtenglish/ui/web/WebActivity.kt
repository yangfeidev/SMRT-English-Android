package com.yangfei.smrtenglish.ui.web

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.databinding.ActivityWebBinding
import com.yangfei.smrtenglish.ui.library.CourseDetailViewModel
import com.yangfei.smrtenglish.util.WebViewHelper

/**
 * 通用网页承载页（对齐 iOS WebPageScreen → WebViewScreen）
 *
 * 用内嵌 WebView 打开官网页面；同步登录 Cookie 保证登录态带入。
 * 返回按钮：WebView 有内部浏览历史（页内跳转）先 goBack 逐级返回，
 * 无历史才退出页面（系统返回键与标题栏返回同源）；
 * 顶部细进度条随 onProgressChanged 伸缩（对齐 iOS WebViewProgressOverlay）。
 */
class WebActivity : BaseActivity<ActivityWebBinding, CourseDetailViewModel>() {

    override fun initView(root: View, savedInstanceState: Bundle?) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        requireTitleBar {
            it.setTitle(title)
            it.setOnBackClickListener { handleBack() }
        }
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        WebViewHelper.setupAndLoad(
            binding.webView,
            WebViewHelper.PageConfig(url = url, syncCookies = true),
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
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_URL = "extra_url"
    }
}
