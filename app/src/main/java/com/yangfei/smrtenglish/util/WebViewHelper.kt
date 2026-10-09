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
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.ProgressBar
import androidx.core.view.isVisible
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.http.Request
import org.json.JSONObject

/**
 * WebView 公共配置（对齐 iOS WebViewRepresentable）
 * - 同步登录态 Cookie（OkHttp 的 CookieJar 与 WebView 两套存储互不相通）
 * - 注入 CSS 隐藏官网站点头部并修复课时页栅格布局
 * - 教师端宽表格页：固定布局视口 + 内容宽度收缩 + 等比缩放（viewport 脚本）
 * - 页面顶部加载进度条（onProgressChanged，对齐 iOS WebViewProgressTracker）
 */
object WebViewHelper {

    // MARK: - WebView 预热（对齐 iOS WebViewRepresentable.warmUp）

    /** 预热用的临时 WebView（warmUp 创建，2s 后释放引用） */
    private var warmUpWebView: WebView? = null

    /**
     * 主线程 Handler（warmUp 用）。
     * lazy：本 object 里还有一批纯字符串逻辑（CSS/JS 构造），JVM 单测会直接引用它们；
     * 若在类初始化阶段就 Handler(Looper.getMainLooper())，纯 JVM 环境没有 Looper 会抛
     * ExceptionInInitializerError，导致这些不依赖 Android 的断言无法测试。
     */
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

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
     * 页面级 WebChromeClient：对话框桥接 + 加载进度回调（对齐 iOS
     * WebViewProgressTracker 的 KVO estimatedProgress）。
     */
    fun pageChromeClient(onProgress: (Int) -> Unit): WebChromeClient =
        object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                onProgress(newProgress)
            }

            override fun onJsAlert(
                webView: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val wv = webView ?: return false
                dialogChromeClient.onJsAlert(wv, url, message, result)
                return true
            }

            override fun onJsConfirm(
                webView: WebView?,
                url: String?,
                message: String?,
                result: JsResult?
            ): Boolean {
                val wv = webView ?: return false
                dialogChromeClient.onJsConfirm(wv, url, message, result)
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
                return dialogChromeClient.onJsPrompt(wv, url, message, defaultValue, result)
            }
        }

    // MARK: - CSS 常量（对齐 iOS WebViewRepresentable 各 CSS 静态属性）

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

    /**
     * 班级子页通用隐藏 CSS（教师端 WebView 承载页 & 学生端补齐页共用）：
     * - .leftside：页面内左侧栏（含手机形态的汉堡按钮 navbar-toggle、班级名、
     *   #menu 折叠菜单——由官网 JS 注入 DOM，CSS 选择器对动态元素同样生效）；
     *   隐藏后 .rightside 撑满整行，避免右侧空出 25% 列宽
     * - footer：站点深色页脚（链接矩阵 + logo）
     * - h1.page-title：页面大标题（App 顶部导航已有班级名，避免重复）
     */
    const val CLASS_PAGE_HIDE_CHROME_CSS =
        ".leftside{display:none !important;}" +
        ".rightside{width:100% !important;}" +
        "footer{display:none !important;}" +
        "h1.page-title{display:none !important;}"

    /** Assessments 页右上角 Add Assessment / Help 圆形图标按钮（教师端操作入口，App 内不用） */
    const val CLASS_PAGE_HIDE_CIRCLE_BUTTONS_CSS =
        ".rightside div[class*='col-'] > a.btn-circle{display:none !important;}"

    /**
     * 教师端 Attendance 记录表列宽修正：
     * 官网 th 自带 width 属性（Date/Full Name/Ref/Status 四列各 20%/40%/20%/20%），
     * Ref 列手机端常为空仍占 ~205px——解除 width 属性按内容分配，
     * Ref 列（td 内 .ellipsis.s75）单独限宽 90px
     */
    const val TEACHER_ATTENDANCE_COLUMNS_CSS =
        ".rightside table th[width],.rightside table td[width]{width:auto !important;}" +
        ".rightside table td:has(> div.ellipsis.s75){width:90px !important;}"

    /**
     * 教师端宽表格收缩：官网 table width:100% 拉满固定视口（1024），
     * 内容窄的列被均分拉出大片列间空白——收缩为按内容自适应宽度，
     * 配合 viewportShrinkLowerBound 收缩视口，消除空旷区域与超长横向滚动
     */
    const val TEACHER_TABLE_SHRINK_CSS =
        ".rightside table{width:auto !important;min-width:0 !important;}"

    /**
     * 宽视口页横向平移解锁（Android 专属；iOS 无需）。
     *
     * 官网样式自带 html{overflow-x:hidden} 与 body{overflow-x:hidden}：
     * iOS 的横向平移走 WKWebView.scrollView（脱离文档 overflow），不受影响；
     * 而 Android WebView 的横向平移本质是**文档滚动容器的水平滚动**，
     * overflow-x:hidden 会把 scrollWidth 截断成 == clientWidth
     * （横向滚动范围恒为 0）——meta viewport 即使已切到 992，
     * 一指左右拖动也完全划不动（真机 + DevTools A/B 实测：注入后
     * window.scrollX 0 → 272，可看到右侧被挡住的列）。
     *
     * 必须作用于 html（只改 body 实测无效）；仅挂到固定宽视口页，
     * 其它页面零影响。与 iOS 的可见差异：无表格的窄页不产生横向溢出，
     * overflow-x:auto 不会带来多余的横滑。
     */
    const val WIDE_VIEWPORT_PAN_CSS = "html{overflow-x:auto !important;}"

    // MARK: - 注入 JS

    /**
     * 页面加载完成后执行的 JS：head 一就绪就挂上 style，首屏渲染前生效。
     *
     * CSS 经 JSON 编码为合法 JS 字符串字面量——内容含单引号/双引号/换行均安全
     * （踩坑：新 CSS 的 [class*='col-'] 单引号会截断单引号 JS 字符串，
     * 整段注入脚本 SyntaxError，header 隐藏与 extraCSS 一起失效；iOS 侧
     * 已于 2026-10-09 用 JSONEncoder 修复，这里对齐用 JSONObject.quote）
     */
    fun hideHeaderJs(extraCss: String = ""): String {
        val css = BASE_HIDE_CSS + extraCss
        val literal = JSONObject.quote(css)
        return "(function(){" +
            "var style=document.createElement('style');" +
            "style.textContent=" + literal + ";" +
            "(function append(){if(document.head){document.head.appendChild(style);}else{setTimeout(append,0);}})();" +
            "})();"
    }

    /**
     * 官网姓名西式「名 姓」→ 中式「姓名」（仅两段纯汉字时交换，
     * 与 Kotlin 侧 normalizeChineseName 规则一致）。
     * 覆盖两类服务端静态输出：打卡卡片 p.miniprofile-title、
     * 记录表 a[href*="view=student"] strong span
     * （对齐 iOS WebViewRepresentable.chineseNameNormalizeJS）
     */
    const val CHINESE_NAME_NORMALIZE_JS =
        "(function() {" +
        "  function cnName(s) {" +
        "    var parts = s.replace(/\\s+/g, ' ').trim().split(' ');" +
        "    if (parts.length === 2 &&" +
        "        /^[\\u4e00-\\u9fa5]{1,4}$/.test(parts[0]) &&" +
        "        /^[\\u4e00-\\u9fa5]{1,4}$/.test(parts[1])) {" +
        "      return parts[1] + parts[0];" +
        "    }" +
        "    return null;" +
        "  }" +
        "  function fix(el) {" +
        "    var name = cnName(el.textContent);" +
        "    if (name) { el.textContent = name; }" +
        "  }" +
        "  function run() {" +
        "    document.querySelectorAll('p.miniprofile-title').forEach(fix);" +
        "    document.querySelectorAll('a[href*=\"view=student\"] strong span').forEach(fix);" +
        "  }" +
        "  if (document.readyState === 'loading') {" +
        "    document.addEventListener('DOMContentLoaded', run);" +
        "  } else { run(); }" +
        "  window.addEventListener('load', run);" +
        "})();"

    /**
     * 视口脚本：先按官网原生 device-width 渲染；页面含数据表格
     * （.rightside table）后把视口切到 [shrinkLowerBound, fixedViewportWidth]
     * （可选 displayScale 等比缩放）；无表格（打卡卡片流/空态）→ 保持手机版自适应。
     * minimum-scale 阻止 WebView 的 fit-to-width 自动缩小，强制等比渲染 + 左右滚动。
     *
     * 与 iOS 的差异（Android 专属补偿，两处）：
     * 1) iOS 用 documentStart 注入，load 监听必然早于 load 事件；Android 只能在
     *    onPageStarted 之后异步 evaluateJavascript，快速/缓存加载时脚本可能晚于
     *    load 才执行 → 改为有界重试，直到表格出现。
     * 2) 官网 JS 会在我们之后重写 viewport meta（真机 360dp 实测：脚本已注入、
     *    横向 CSS 也已生效，但 viewport 最终停在 device-width，表格被手机版 CSS
     *    压到 288px，横向无溢出 = 划不动）→ 用**看守定时器**在窗口期内反复校正：
     *    只要表格存在且当前 viewport 不是目标值就重设，避免被官网覆盖后失效。
     *
     * 另注：内容宽度在手机版布局下已被官网抹平（实测 #content 仅 372px），
     * 故量宽只用于「内容比下限宽」的加分项，量不到就回落到下限。
     */
    fun viewportJs(
        fixedViewportWidth: Int,
        shrinkLowerBound: Int?,
        displayScale: Double?
    ): String {
        val scale = displayScale ?: 1.0
        val shrinkBlock = if (shrinkLowerBound != null) {
            "  var tries = 0;" +
            "  function desiredSpec() {" +
            "    var c = document.querySelector('#content');" +
            "    var table = document.querySelector('.rightside table');" +
            "    if (!table) { return null; }" +
            "    var w = 0;" +
            "    if (c) {" +
            "      c.style.width = 'max-content';" +
            "      w = Math.ceil(c.getBoundingClientRect().width);" +
            "      c.style.width = '';" +
            "    }" +
            "    var target = Math.max($shrinkLowerBound, Math.min(w || $shrinkLowerBound, $fixedViewportWidth));" +
            "    return 'width=' + target + ', initial-scale=$scale, minimum-scale=$scale';" +
            "  }" +
            "  function currentVp() {" +
            "    var m = document.querySelector('meta[name=\"viewport\"]');" +
            "    return m ? m.getAttribute('content') : null;" +
            "  }" +
            "  function adjust() {" +
            "    var spec = desiredSpec();" +
            "    if (spec === null) { return false; }" +
            "    if (currentVp() !== spec) { setViewport(spec); window.scrollTo(0, 0); }" +
            "    return true;" +
            "  }" +
            "  function tick() {" +
            "    var hit = adjust();" +
            "    if (hit && ++tries > 3) { return; }" +
            "    if (tries < 20) { setTimeout(tick, 500); }" +
            "  }" +
            "  window.addEventListener('load', tick);" +
            "  setTimeout(tick, 900);" +
            "  if (document.readyState !== 'loading') { tick(); }"
        } else ""
        return "(function() {" +
            "  function setViewport(spec) {" +
            "    var root = document.head || document.documentElement;" +
            // onPageStarted 注入时文档可能还是空的（documentElement/head 均为 null，
            // 真机 360dp 实测：appendChild 抛 TypeError 会让整段脚本中断，
            // 后续 load 监听与重试全部注册不上 → 表格页永远停在手机版）。
            // 与 hideHeaderJs 的 append 重试同样处理：拿不到 root 就下一轮再来。
            "    if (!root) { setTimeout(function() { setViewport(spec); }, 0); return; }" +
            "    var metas = document.querySelectorAll('meta[name=\"viewport\"]');" +
            "    for (var i = 0; i < metas.length; i++) { metas[i].remove(); }" +
            "    var m = document.createElement('meta');" +
            "    m.name = 'viewport';" +
            "    m.content = spec;" +
            "    root.appendChild(m);" +
            "  }" +
            "  setViewport('width=device-width, initial-scale=1');" +
            "  document.addEventListener('DOMContentLoaded', function() {" +
            "    setViewport('width=device-width, initial-scale=1');" +
            "  });" +
            shrinkBlock +
            "})();"
    }

    // MARK: - 页面级承载（对齐 iOS WebViewRepresentable.makeUIView + WebViewScreen）

    /**
     * 页面级 WebView 配置（对齐 iOS WebViewRepresentable 的入参集合）
     */
    data class PageConfig(
        /** 加载的页面 URL */
        val url: String,
        /** 是否同步登录 cookie（需要登录态的页面传 true） */
        val syncCookies: Boolean = false,
        val pageBackgroundColor: Int = Color.WHITE,
        val hideSiteHeader: Boolean = false,
        /** 追加注入的 CSS */
        val extraCss: String = "",
        /**
         * 数据表格页宽视口（教师端宽表格页用）：null = 官网手机版自适应；
         * 非 null 时先按 device-width 渲染，页面 load 后仅在存在数据表格时
         * 把视口收缩到 [viewportShrinkLowerBound, fixedViewportWidth]，
         * 竖屏屏宽不够时 WebView 内左右滑动查看
         */
        val fixedViewportWidth: Int? = null,
        /** 视口收缩下限（Assignments 需 ≥992、Attendance 需 ≥768，见 iOS 注释） */
        val viewportShrinkLowerBound: Int? = null,
        /**
         * 宽视口的显示缩放（0.9 = 内容整体缩到 90%，手机观感更合适）；
         * 仅手机生效（≥600dp 的大屏 1:1 渲染，对齐 iOS 的 iPad 判定）
         */
        val viewportScale: Double? = null,
        /** 页面加载完成后执行的 JS（如 chineseNameNormalizeJS） */
        val pageLoadJs: String? = null,
    )

    @SuppressLint("SetJavaScriptEnabled")
    fun configWebView(
        webView: WebView,
        pageBackgroundColor: Int = Color.WHITE
    ) {
        // debug 包开 WebView 远程调试（chrome://inspect），线上不受影响
        if (com.yangfei.smrtenglish.BuildConfig.DEBUG) {
            WebView.setWebContentsDebuggingEnabled(true)
        }
        webView.setBackgroundColor(pageBackgroundColor)
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.useWideViewPort = true
        settings.loadWithOverviewMode = true
        // 缩放手势必须开启（displayZoomControls=false 只隐藏 +/- 按钮）：
        // 教师宽表格页 meta viewport width=992 > 屏宽，横向平移走的就是
        // 这套缩放/平移手势系统——关掉后触摸拖动完全滚不动（JS scrollTo 正常）
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
    }

    /**
     * 页面级 WebView 装配：基础配置 + 对话框桥接/进度回调 + CSS/JS 注入 + Cookie 同步后加载。
     * 返回值供页面挂 WebViewClient 扩展（如需要）。
     */
    fun setupAndLoad(
        webView: WebView,
        config: PageConfig,
        onProgress: (Int) -> Unit
    ): WebViewClient {
        configWebView(webView, config.pageBackgroundColor)
        // 固定视口页（教师宽表格）关闭 overview 模式：overview 的 fit-to-width
        // 会和 meta viewport 的 initial-scale=0.9 互相拉扯（闪动 + 缩放不对）
        if (config.fixedViewportWidth != null) {
            webView.settings.loadWithOverviewMode = false
        }
        webView.webChromeClient = pageChromeClient(onProgress)
        val hideJs = hideHeaderJs(config.extraCss)
        val viewportJs = config.fixedViewportWidth?.let {
            // 显示缩放：所有设备统一生效（对齐 iOS 的「非 iPad 一律缩放」）。
            // 不用 smallestScreenWidthDp >= 600 做豁免——该判据在「高分辨率 + 低密度」
            // 的机型上会误报平板（实测 1080×1920 @240dpi 报 720dp，实为手机），
            // 导致本应缩放到 0.9 的机器按 1:1 渲染，字号与可见列数和 iOS 不一致。
            viewportJs(it, config.viewportShrinkLowerBound, config.viewportScale)
        }
        val pageLoadJs = config.pageLoadJs
        val client = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                // 视口脚本只在这里注入一次（对齐 iOS 的 documentStart WKUserScript）：
                // onPageFinished 里再跑一遍会先把视口重置回 device-width 再切回
                // 固定视口——「手机版↔桌面版」来回闪（闪动的根源）
                viewportJs?.let { view?.evaluateJavascript(it, null) }
                // CSS 尽早挂上，站点 header 首屏渲染前隐藏（脚本内部自带 head 重试）
                if (config.hideSiteHeader || config.extraCss.isNotEmpty()) {
                    view?.evaluateJavascript(hideJs, null)
                }
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                // CSS 兜底再挂一次（同名规则重复无视觉影响；防早期注入被站点覆盖）
                if (config.hideSiteHeader || config.extraCss.isNotEmpty()) {
                    view?.evaluateJavascript(hideJs, null)
                }
                pageLoadJs?.let { view?.evaluateJavascript(it, null) }
            }

            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean = false
        }
        webView.webViewClient = client
        if (config.syncCookies) syncCookies()
        webView.loadUrl(config.url)
        return client
    }

    /** ≥600dp 视为平板/大屏（对齐 iOS 的 iPad 判定 userInterfaceIdiom == .pad） */
    fun isTablet(context: android.content.Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    /** 进度条通用驱动：0~100 显示，100 隐藏（对齐 iOS WebViewProgressOverlay） */
    fun bindProgress(progressBar: ProgressBar): (Int) -> Unit = { progress ->
        progressBar.progress = progress
        progressBar.isVisible = progress in 1..99
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
}
