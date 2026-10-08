package com.yangfei.smrtenglish.util

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.http.Request

/**
 * WebView 公共配置（对齐 iOS WebViewRepresentable）
 * - 同步登录态 Cookie（OkHttp 的 CookieJar 与 WebView 两套存储互不相通）
 * - 注入 CSS 隐藏官网站点头部并修复课时页栅格布局
 */
object WebViewHelper {

    // MARK: - WebView 预热（对齐 iOS WebViewRepresentable.warmUp）

    /** 预热用的临时 WebView（warmUp 创建，2s 后释放引用） */
    private var warmUpWebView: WebView? = null

    private val mainHandler = Handler(Looper.getMainLooper())

    /**
     * App 启动后调用：后台创建一个加载空白页的 WebView，提前拉起 WebView
     * 渲染进程，把首次冷启动成本挪出用户点击路径（首次进课时详情不再卡顿）。
     */
    fun warmUp(context: android.content.Context) {
        if (warmUpWebView != null) return
        val webView = WebView(context.applicationContext)
        warmUpWebView = webView
        webView.loadData("", "text/html", "utf-8")
        // 2s 后释放（渲染进程已拉起，WebView 本身不再需要）
        mainHandler.postDelayed({ warmUpWebView = null }, 2000)
    }

    // MARK: - JS 对话框桥接（对齐 iOS DialogDelegate）

    /**
     * 把网页 JS 的 alert / confirm / prompt 桥接为原生弹窗。
     * 不设置 WebChromeClient 时 WebView 静默吞掉这些调用（confirm 恒返回 false），
     * 官网依赖 confirm 的提交按钮（练习「Complete」等）会点不出来。
     */
    private val dialogChromeClient = object : WebChromeClient() {

        private fun dialog(
            webView: WebView,
            message: String,
            addTextField: Boolean = false,
            confirmTitle: String = webView.context.getString(R.string.confirm),
            result: (String?) -> Unit
        ) {
            val edit = if (addTextField) EditText(webView.context) else null
            AlertDialog.Builder(webView.context)
                .setMessage(message)
                .apply { edit?.let { it.hint = ""; setView(it) } }
                .setNegativeButton(R.string.cancel) { _, _ -> result(null) }
                .setPositiveButton(confirmTitle) { _, _ -> result(edit?.text?.toString() ?: "") }
                .setOnCancelListener { result(null) }
                .show()
        }

        override fun onJsAlert(
            webView: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val wv = webView ?: return false
            dialog(wv, message ?: "") { result?.confirm() }
            return true
        }

        override fun onJsConfirm(
            webView: WebView?,
            url: String?,
            message: String?,
            result: JsResult?
        ): Boolean {
            val wv = webView ?: return false
            dialog(wv, message ?: "") { confirmed ->
                // 非 null（含空串）= 确定，null = 取消
                if (confirmed != null) result?.confirm() else result?.cancel()
            }
            return true
        }

        override fun onJsPrompt(
            webView: WebView?,
            url: String?,
            message: String?,
            defaultValue: String?,
            result: JsPromptResult?
        ): Boolean {
            val wv = webView ?: return false
            dialog(wv, message ?: defaultValue ?: "", addTextField = true) { text ->
                if (text != null) result?.confirm(text) else result?.cancel()
            }
            return true
        }
    }

    /**
     * 隐藏站点头部 + 课时页布局修复的基础 CSS（与 iOS 保持一致）
     *
     * 1) header 整个 display:none；配套 #content{margin-top:0}（官网给固定 header 留了 60px 补偿）
     * 2) 课时内容栅格：官网 @media (max-width:480px) 把 #lesson-content.v2 的 .col1~.col12
     *    全改 width:100%，这里按桌面端数值用 !important 声明回去
     * 3) 手机端（≤480px）专属微调：单词卡一排 2 个、点图活动大小失衡修复、画板撑满整行
     * 4) 多图行 figure.image25/30/35：恢复桌面宽度
     */
    const val BASE_HIDE_CSS = "header{display:none !important;}" +
        "#content{margin-top:0 !important;}" +
        "#content #lesson-content.v2 .col1{width:5.5833333% !important;}" +
        "#content #lesson-content.v2 .col2{width:14.1666666% !important;}" +
        "#content #lesson-content.v2 .col2-4{width:17.6% !important;}" +
        "#content #lesson-content.v2 .col3{width:22.75% !important;}" +
        "#content #lesson-content.v2 .col4{width:31.333333% !important;}" +
        "#content #lesson-content.v2 .col5{width:39.916666% !important;}" +
        "#content #lesson-content.v2 .col6{width:48.5% !important;}" +
        "#content #lesson-content.v2 .col7{width:57.083333% !important;}" +
        "#content #lesson-content.v2 .col8{width:65.666666% !important;}" +
        "#content #lesson-content.v2 .col9{width:74.25% !important;}" +
        "#content #lesson-content.v2 .col10{width:82.833333% !important;}" +
        "#content #lesson-content.v2 .col11{width:91.416666% !important;}" +
        "#content #lesson-content.v2 .col12{width:100% !important;}" +
        "html #content #lesson-content.v2 figure.image25{width:25% !important;}" +
        "html #content #lesson-content.v2 figure.image30{width:30% !important;}" +
        "html #content #lesson-content.v2 figure.image35{width:35% !important;}" +
        "@media only screen and (max-width:480px){#content #lesson-content.v2 .col3{width:47% !important;}}" +
        "@media only screen and (max-width:480px){" +
        "#content #lesson-content.v2 .col3 > figure.view{width:63% !important;margin:0 auto !important;}" +
        "#content #lesson-content.v2 .col3 + .col9{width:50% !important;}" +
        "#content #lesson-content.v2 .col3 + .col9 .click.col2{width:46% !important;}" +
        "#content #lesson-content.v2 .col6:has(> .draw){width:100% !important;}" +
        "}"

    /** 页面加载完成后执行的 JS：head 一就绪就挂上 style，首屏渲染前生效 */
    fun hideHeaderJs(extraCss: String = ""): String {
        val css = BASE_HIDE_CSS + extraCss
        return "(function(){" +
            "var style=document.createElement('style');" +
            "style.textContent='" + css + "';" +
            "(function append(){if(document.head){document.head.appendChild(style);}else{setTimeout(append,0);}})();" +
            "})();"
    }

    /** 把 OkHttp 侧的登录 Cookie 写入 WebView（不同步会以游客态打开页面） */
    fun syncCookies() {
        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        Request.cookieJar.allCookies().forEach { cookie ->
            cookieManager.setCookie(UrlConstant.BASE_URL, "${cookie.name}=${cookie.value}")
        }
        cookieManager.flush()
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun configWebView(
        webView: WebView,
        pageBackgroundColor: Int = Color.WHITE,
        hideSiteHeader: Boolean = false,
        extraCss: String = ""
    ) {
        webView.setBackgroundColor(pageBackgroundColor)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        // JS 对话框桥接：官网练习「Complete」提交走 confirm()，未设置时
        // WebView 静默返回 false → 提交永远不执行（iOS 真机踩坑，安卓同理）
        webView.webChromeClient = dialogChromeClient
        webView.isVerticalScrollBarEnabled = true
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }
}
