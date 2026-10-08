package com.yangfei.smrtenglish.ui.web

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.core.view.isVisible
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.databinding.ActivityWebBinding
import com.yangfei.smrtenglish.ui.library.CourseDetailViewModel
import com.yangfei.smrtenglish.util.WebViewHelper

/**
 * 通用网页承载页（对齐 iOS WebPageScreen）
 *
 * 用内嵌 WebView 打开官网页面；同步登录 Cookie 保证登录态带入。
 */
class WebActivity : BaseActivity<ActivityWebBinding, CourseDetailViewModel>() {

    override fun initView(root: View, savedInstanceState: Bundle?) {
        val title = intent.getStringExtra(EXTRA_TITLE) ?: ""
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        requireTitleBar { it.setTitle(title) }

        WebViewHelper.syncCookies()
        WebViewHelper.configWebView(binding.webView, pageBackgroundColor = Color.WHITE)

        binding.webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                binding.progressBar.isVisible = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                binding.progressBar.isVisible = false
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }

        if (url.isNotEmpty()) binding.webView.loadUrl(url)
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
