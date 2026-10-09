package com.yangfei.smrtenglish.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 班级模块 UI 一致性回归测试（对齐 iOS 的排布/脚本契约）。
 *
 * 这些断言覆盖的是「真机上难以穷举、但容易改错」的纯逻辑：
 * 自适应列数算法、宽表格页的视口脚本与横向平移 CSS。
 */
class ClassTabParityTest {

    // MARK: - 自适应网格列数（对齐 SwiftUI GridItem(.adaptive(minimum:))）

    /** 学生 Class List：min 150 / spacing 12（iOS LazyVGrid adaptive(150)） */
    private fun classListColumns(screenWidthDp: Int) =
        UiUtils.adaptiveColumns(screenWidthDp - 32, minItemWidthDp = 150, spacingDp = 12)

    @Test
    fun classList_phone_width_should_be_two_columns() {
        // 360dp 手机：可用 328 → 2*150+12 = 312 ≤ 328，3 列需 474 > 328
        assertEquals(2, classListColumns(360))
    }

    @Test
    fun classList_large_phone_should_be_four_columns() {
        // 实测设备 720dp（1080px @240dpi）：可用 688 → 4*150+3*12 = 636 ≤ 688
        assertEquals(4, classListColumns(720))
    }

    @Test
    fun classList_narrow_width_should_never_go_below_one_column() {
        assertEquals(1, classListColumns(100))
        assertEquals(1, classListColumns(0))
    }

    @Test
    fun classList_columns_must_satisfy_ios_layout_equation() {
        // 对一批屏宽验证不变量：n*min + (n-1)*spacing ≤ available < (n+1)*min + n*spacing
        val min = 150
        val spacing = 12
        for (screen in listOf(320, 360, 375, 393, 411, 480, 600, 720, 800, 834, 1024)) {
            val available = screen - 32
            val n = classListColumns(screen)
            val used = n * min + (n - 1).coerceAtLeast(0) * spacing
            assertTrue("screen=$screen n=$n used=$used must fit in $available", used <= available)
            val nextUsed = (n + 1) * min + n * spacing
            assertTrue("screen=$screen n=$n should be maximal (next=$nextUsed)", nextUsed > available)
        }
    }

    /** 班级卡内成员网格：min 64 / spacing 10（iOS adaptive(64)），最少 4 列 */
    @Test
    fun member_grid_keeps_ios_minimum_of_four_columns() {
        // UiUtils.memberGridColumns 是该算法的 Context 封装：验证同样的纯函数形态
        val screen = 360
        val available = screen - 64
        val n = UiUtils.adaptiveColumns(available, minItemWidthDp = 64, spacingDp = 10)
            .coerceAtLeast(4)
        assertEquals(4, n)
    }

    // MARK: - 宽表格页：横向平移 CSS（P0 修复的契约）

    /**
     * 官网自带 html{overflow-x:hidden}。iOS 的平移走 WKWebView.scrollView 不受影响，
     * Android 的平移是文档滚动容器的水平滚动——必须把 html 的 overflow-x 放开，
     * 否则 scrollWidth == clientWidth，横向范围为 0（真机 A/B 实测：0 → 272）。
     */
    @Test
    fun wide_viewport_pan_css_must_target_html() {
        val css = WebViewHelper.WIDE_VIEWPORT_PAN_CSS
        assertTrue("必须作用于 html（只改 body 实测无效）", css.contains("html{"))
        assertTrue("必须是 auto 或 visible", css.contains("overflow-x:auto") || css.contains("overflow-x:visible"))
        assertTrue("需要 !important 压过官网同属性声明", css.contains("!important"))
    }

    // MARK: - 视口脚本契约

    @Test
    fun viewport_script_should_shrink_to_content_width_within_bounds() {
        val js = WebViewHelper.viewportJs(
            fixedViewportWidth = 1024,
            shrinkLowerBound = 992,
            displayScale = 0.9
        )
        assertTrue("宽度上限 1024", js.contains("Math.min(w || 992, 1024)"))
        assertTrue("宽度下限 992（保住 Start/Due 列）", js.contains("Math.max(992,"))
        assertTrue("等比缩放 0.9", js.contains("initial-scale=0.9, minimum-scale=0.9"))
        assertTrue("必须删除官网原有 viewport meta", js.contains("remove()"))
    }

    @Test
    fun viewport_script_scale_defaults_to_one_when_absent() {
        val js = WebViewHelper.viewportJs(1024, 992, null)
        assertTrue(js.contains("initial-scale=1.0, minimum-scale=1.0"))
    }

    /**
     * Android 只能在 onPageStarted 之后异步 evaluateJavascript，快速/缓存加载时
     * 脚本可能晚于 load 执行；若 adjust 在表格尚未入 DOM 时直接放弃，表格页会
     * 永久停在手机版布局（真机实测到此状态）。故必须有界重试。
     */
    @Test
    fun viewport_script_should_retry_until_table_appears() {
        val js = WebViewHelper.viewportJs(1024, 992, 0.9)
        assertTrue("表格缺失时返回未命中", js.contains("return false;"))
        assertTrue("未命中要重试", js.contains("tick"))
        assertTrue("重试需有界（不能无限定时器）", js.contains("tries < 20"))
        assertTrue(
            "若脚本晚于 load 执行，需立即补跑一次",
            js.contains("document.readyState !== 'loading'")
        )
    }

    /**
     * 真机（360dp）实测：官网 JS 会在我们之后重写 viewport meta，最终停在
     * device-width，表格被手机版 CSS 压到 288px → 横向无溢出、划不动。
     * 故必须能检测「当前 viewport ≠ 目标」并重设，而不是「设过一次就不管」。
     */
    @Test
    fun viewport_script_should_re_assert_when_site_overwrites_viewport() {
        val js = WebViewHelper.viewportJs(1024, 992, 0.9)
        assertTrue("需读取当前 viewport 做比对", js.contains("currentVp()"))
        assertTrue("与目标不一致时重设", js.contains("if (currentVp() !== spec)"))
        assertTrue("重设后回滚到顶部", js.contains("window.scrollTo(0, 0)"))
    }

    /**
     * 手机版布局下官网 CSS 已把 #content 抹平（实测 372px），量宽不可信；
     * 量不到时必须回落到下限，而不是小于 320 就放弃切换。
     */
    @Test
    fun viewport_script_should_fall_back_to_lower_bound_when_measure_is_unreliable() {
        val js = WebViewHelper.viewportJs(1024, 992, 0.9)
        assertTrue("量宽失败回落到下限", js.contains("w || 992"))
        assertFalse("不能再用 w > 320 作为切换门槛", js.contains("if (w > 320)"))
    }

    @Test
    fun viewport_script_without_lower_bound_should_not_shrink() {
        val js = WebViewHelper.viewportJs(1024, null, 0.9)
        assertFalse("无下限则不做收缩逻辑", js.contains("tick"))
        assertFalse(js.contains("Math.min(w || 992, 1024)"))
    }

    // MARK: - 显示缩放：仅手机缩放，平板 1:1（对齐 iOS iPad 判定）

    /**
     * iOS：`UIDevice.userInterfaceIdiom == .pad ? nil : viewportScale`
     * —— 平板传 nil（1:1 不缩放），只有手机才缩到 0.9。
     * 回归背景：曾把平板豁免整体删掉，导致平板也按 0.9 渲染
     * （实测平板 sw720dp 时 initial-scale=0.9，内容被拉宽、右侧留白）。
     */
    @Test
    fun tablet_should_not_apply_viewport_scale() {
        assertEquals(null, WebViewHelper.effectiveViewportScale(isTablet = true, viewportScale = 0.9))
    }

    @Test
    fun phone_should_apply_viewport_scale() {
        assertEquals(0.9, WebViewHelper.effectiveViewportScale(isTablet = false, viewportScale = 0.9))
        assertEquals(0.9, WebViewHelper.effectiveViewportScale(isTablet = false, viewportScale = 0.9)!!, 1e-9)
    }

    @Test
    fun no_scale_passed_stays_null_on_both() {
        assertEquals(null, WebViewHelper.effectiveViewportScale(isTablet = false, viewportScale = null))
        assertEquals(null, WebViewHelper.effectiveViewportScale(isTablet = true, viewportScale = null))
    }

    /** 平板 → 视口脚本应产出 1:1（initial-scale=1.0），而不是 0.9 */
    @Test
    fun tablet_viewport_script_renders_one_to_one() {
        val scale = WebViewHelper.effectiveViewportScale(isTablet = true, viewportScale = 0.9)
        val js = WebViewHelper.viewportJs(1024, 992, scale)
        assertTrue("平板应为 1:1", js.contains("initial-scale=1.0, minimum-scale=1.0"))
        assertFalse("平板不该出现 0.9", js.contains("initial-scale=0.9"))
    }

    /** 手机 → 视口脚本应产出 0.9 */
    @Test
    fun phone_viewport_script_renders_scaled() {
        val scale = WebViewHelper.effectiveViewportScale(isTablet = false, viewportScale = 0.9)
        val js = WebViewHelper.viewportJs(1024, 992, scale)
        assertTrue("手机应为 0.9", js.contains("initial-scale=0.9, minimum-scale=0.9"))
    }

    // MARK: - 同页 tab 导航折叠（返回键应直接退出本页）

    /**
     * 官网 Assignments 的 Scheduled/Shared/Complete 是 `<a href="?tab=...">`
     * 真实导航，每切一次 history +1（实测 3→4→5），导致返回键逐级退回
     * 切换过的标签。折叠脚本把同 path 的 ?tab= 导航改为 location.replace。
     */
    @Test
    fun collapse_script_should_replace_history_and_keep_other_nav() {
        val js = WebViewHelper.COLLAPSE_TAB_HISTORY_JS
        assertTrue("同 path 判定", js.contains("u.pathname !== location.pathname"))
        assertTrue("tab 参数判定", js.contains("searchParams.has('tab')"))
        assertTrue("拦截点击默认行为", js.contains("ev.preventDefault()"))
        assertTrue("用 replace 不入栈", js.contains("location.replace(a.href)"))
        assertTrue("捕获阶段注册，先于站点自身 handler", js.contains("}, true);"))
    }

    /**
     * 拼接式 JS 是「一行」，注释必须用斜杠星号块注释而非双斜杠行注释——
     * 行注释会把同行后续语句一并注释掉，脚本静默失效。
     * （曾真实踩坑：监听器注册了但什么都不做，defaultPrevented 恒为 false，
     * history 照旧 3→4→5 增长。）
     */
    @Test
    fun concatenated_js_must_not_use_line_comments() {
        val scripts = mapOf(
            "COLLAPSE_TAB_HISTORY_JS" to WebViewHelper.COLLAPSE_TAB_HISTORY_JS,
            "CHINESE_NAME_NORMALIZE_JS" to WebViewHelper.CHINESE_NAME_NORMALIZE_JS,
            "WIDE_VIEWPORT_PAN_CSS" to WebViewHelper.WIDE_VIEWPORT_PAN_CSS,
        )
        scripts.forEach { (name, js) ->
            assertFalse(
                "$name 含 // 行注释——拼接后为单行，会把后续语句注释掉",
                Regex("""(^|[^:'"])//""").containsMatchIn(js)
            )
        }
    }

    /**
     * onPageStarted 注入时文档可能仍是空的（documentElement/head 都为 null）。
     * 真机 360dp 实测：appendChild(null) 抛 TypeError 会中断整段脚本，
     * 后续 load 监听与重试全部注册不上 → 表格页永远停在手机版布局。
     */
    @Test
    fun viewport_script_must_survive_empty_document() {
        val js = WebViewHelper.viewportJs(1024, 992, 0.9)
        assertTrue(
            "拿不到 root 时必须延后重试而不是直接 appendChild",
            js.contains("if (!root) { setTimeout")
        )
        assertFalse(
            "不能再对可能为 null 的 head/documentElement 直接 appendChild",
            js.contains("(document.head || document.documentElement).appendChild(m);")
        )
    }

    // MARK: - 子 Tab 定义（与 iOS ClassTab 对齐）

    @Test
    fun class_tabs_should_match_ios_order_and_paths() {
        val expected = listOf(
            "course" to "Course",
            "classlist" to "Class List",
            "attendance" to "Attendance",
            "assignments" to "Assignments",
            "evaluation" to "Assessments", // 注意：官网路径是 evaluation
            "gradebook" to "Gradebook",
            "classinfo" to "Class Info"
        )
        val actual = com.yangfei.smrtenglish.bean.ClassTab.entries.map { it.path to it.title }
        assertEquals(expected, actual)
    }

    @Test
    fun student_should_see_six_tabs_and_teacher_seven() {
        val all = com.yangfei.smrtenglish.bean.ClassTab.entries
        val studentTabs = all.filter { it != com.yangfei.smrtenglish.bean.ClassTab.GRADEBOOK }
        assertEquals(6, studentTabs.size)
        assertEquals(7, all.size)
    }
}
