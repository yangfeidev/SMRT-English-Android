package com.yangfei.smrtenglish.ui.classes

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import androidx.activity.addCallback
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.FrameLayout
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.base.manager.SessionManager
import com.yangfei.smrtenglish.bean.ClassAssignment
import com.yangfei.smrtenglish.bean.ClassInfoItem
import com.yangfei.smrtenglish.bean.ClassLesson
import com.yangfei.smrtenglish.bean.ClassTab
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.bean.TeacherClassListMember
import com.yangfei.smrtenglish.databinding.ActivityClassCourseBinding
import com.yangfei.smrtenglish.databinding.ItemClassAssignmentBinding
import com.yangfei.smrtenglish.databinding.ItemClassInfoBinding
import com.yangfei.smrtenglish.databinding.ItemClassTabMemberBinding
import com.yangfei.smrtenglish.databinding.ItemTeacherMemberBinding
import com.yangfei.smrtenglish.ui.classes.adapter.ClassCourseAdapter
import com.yangfei.smrtenglish.ui.library.LessonDetailActivity
import com.yangfei.smrtenglish.util.UiUtils
import com.yangfei.smrtenglish.util.UiUtils.dp
import com.yangfei.smrtenglish.util.WebViewHelper
import com.yangfei.smrtenglish.util.loadCircle
import com.yangfei.smrtenglish.view.AxisLockWebView

/**
 * 班级课程页（对齐 iOS ClassCourseView + ClassCourseTabViews + TeacherTabViews）
 *
 * 可见子 Tab：学生 6 项 / 教师 7 项（多 Gradebook，班级页侧栏 Gradebook 链接判定）。
 * 原生页：Course 单元树（两角色共用）；学生 Class List / Assignments / Class Info；
 * 教师 Class List（Current/Removed 原生列表）。
 * WebView 承载页（登录态 + 隐藏站点 chrome，官网自理免维护）：
 * 两角色 Attendance/Assessments；教师 Assignments/Gradebook。
 * 教师宽表格页（Assignments/Attendance）：固定布局视口 + 内容收缩 + 0.9 缩放，
 * 右下角悬浮钮切横屏（横屏紧凑顶栏：返回按钮与胶囊 tab 合并一行）。
 * 各 Tab 首次切到时才拉取（懒加载），切回使用缓存不再重复请求。
 * 返回按钮：当前 WebView 有内部浏览历史先 goBack 逐级返回，无历史才退出页面。
 */
class ClassCourseActivity : BaseActivity<ActivityClassCourseBinding, ClassCourseViewModel>() {

    private lateinit var adapter: ClassCourseAdapter

    private val klass: StudentClass by lazy {
        intent.getParcelableExtra(EXTRA_CLASS) ?: StudentClass()
    }

    private var courseLibraryId: String = ""

    private var currentTab = ClassTab.COURSE

    /** 是否教师端（缓存角色即时生效；course 页解析的侧栏 Gradebook 链接硬校正） */
    private var isTeacher = SessionManager.isTeacher

    /** Tab chip 缓存（选中高亮 + 点击滚动居中） */
    private val chipViews = HashMap<ClassTab, TextView>()

    /** 教师 wide table 页（横竖屏切换悬浮钮仅这两页，对齐 iOS TeacherWideTableModifier） */
    private val wideTableTabs = setOf(ClassTab.ASSIGNMENTS, ClassTab.ATTENDANCE)

    /**
     * 本页是否**主动**把方向锁成了横屏（仅教师宽表格页的悬浮钮会置位）。
     *
     * 用于区分两种「横屏」：
     * - 设备天然横屏（平板 / 横屏模拟器）：与页面意愿无关，本页不应改动它；
     * - 页面主动锁横屏：离开该 tab / 页面时才需要恢复竖屏。
     *
     * 等价于 iOS 的 OrientationLock.shared.landscapeOnly。
     * 早期实现误用 isLandscape()（设备当前方向）判断，导致平板一进班级页就被
     * 强制扭成竖屏。
     *
     * 直接由 requestedOrientation 派生（唯一事实来源），避免另开布尔量与
     * 系统状态不同步。
     */
    private val forcedLandscape: Boolean
        get() = requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

    // 原生子 Tab 数据缓存（旋转重建后由 LiveData 重放回填）
    private var cachedMembers: List<String>? = null
    private var cachedAssignments: List<ClassAssignment>? = null
    private var cachedClassInfo: List<ClassInfoItem>? = null

    // 教师端 Class List 缓存（Current/Removed 两子页）
    private var cachedTeacherCurrent: List<TeacherClassListMember>? = null
    private var cachedTeacherRemoved: List<TeacherClassListMember>? = null
    private var teacherListTabIsCurrent = true
    private var teacherCurrentCount = 0

    // WebView 子 Tab 缓存（按 tab 各建一个，切回保留已加载内容）
    private val webViews = HashMap<ClassTab, AxisLockWebView>()
    private var activeWebView: AxisLockWebView? = null

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar {
            it.setTitle(klass.name)
            it.setOnBackClickListener { handleBack() }
        }
        binding.btnBackCompact.setOnClickListener { handleBack() }
        binding.btnRotate.setOnClickListener { setOrientation(!isLandscape()) }
        // 系统返回：与标题栏返回同源（WebView 有历史先 goBack，无历史退出页面）
        onBackPressedDispatcher.addCallback(this) { handleBack() }

        adapter = ClassCourseAdapter { lesson -> openLesson(lesson) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        buildTabs()
        // 重试：WebView 承载页重载当前 URL（原先走 selectTab 对 WebView 是空操作，
        // 点了没反应）；原生页强制重新拉取（对齐 iOS ClassTabPageContainer 的重试）
        binding.btnRetry.setOnClickListener { retryCurrentTab() }
    }

    override fun loadData() {
        selectTab(ClassTab.COURSE)
    }

    override fun initObserver() {
        viewModel.courseResult.observe(this) { response ->
            if (response.isSuccessful) {
                val course = response.data
                if (course == null || course.units.isEmpty()) {
                    showTabError(getString(R.string.classes_no_course), ClassTab.COURSE)
                    return@observe
                }
                courseLibraryId = course.courseLibraryId
                requireTitleBar { it.setTitle(course.courseName.ifEmpty { klass.name }) }
                updateTeacherState(course.isTeacher)
                adapter.submit(course.units)
                showContentState()
            } else {
                showTabError(response.message ?: getString(R.string.error_bad_response), ClassTab.COURSE)
            }
        }

        viewModel.classListResult.observe(this) { response ->
            if (!response.isSuccessful) {
                return@observe showTabError(response.message, ClassTab.CLASS_LIST)
            }
            cachedMembers = response.data ?: emptyList()
            if (currentTab == ClassTab.CLASS_LIST && !isTeacher) renderClassList(cachedMembers!!)
        }
        viewModel.assignmentsResult.observe(this) { response ->
            if (!response.isSuccessful) {
                return@observe showTabError(response.message, ClassTab.ASSIGNMENTS)
            }
            cachedAssignments = response.data ?: emptyList()
            if (currentTab == ClassTab.ASSIGNMENTS && !isTeacher) renderAssignments(cachedAssignments!!)
        }
        viewModel.classInfoResult.observe(this) { response ->
            if (!response.isSuccessful) {
                return@observe showTabError(response.message, ClassTab.CLASS_INFO)
            }
            cachedClassInfo = response.data ?: emptyList()
            if (currentTab == ClassTab.CLASS_INFO) renderClassInfo(cachedClassInfo!!)
        }
        viewModel.teacherListResult.observe(this) { response ->
            if (!response.isSuccessful) {
                return@observe showTabError(response.message, ClassTab.CLASS_LIST)
            }
            val page = response.data ?: return@observe
            if (page.isCurrent) {
                cachedTeacherCurrent = page.members
                teacherCurrentCount = page.members.size
            } else {
                cachedTeacherRemoved = page.members
            }
            if (currentTab == ClassTab.CLASS_LIST && isTeacher &&
                teacherListTabIsCurrent == page.isCurrent
            ) {
                renderTeacherClassList(page.isCurrent, page.members)
            }
        }
    }

    // MARK: - 角色与可见 Tab

    /** 可见 tab：教师端多一个 Gradebook（学生端 6 项、教师端 7 项） */
    private fun visibleTabs(): List<ClassTab> =
        ClassTab.entries.filter { it != ClassTab.GRADEBOOK || isTeacher }

    /** 角色硬校正（course 页侧栏 Gradebook 链接），tab 集变化时重建 chip 行 */
    private fun updateTeacherState(teacher: Boolean) {
        if (isTeacher == teacher) return
        isTeacher = teacher
        buildTabs()
        if (currentTab == ClassTab.GRADEBOOK && !isTeacher) {
            selectTab(ClassTab.COURSE)
        } else {
            // 恢复选中高亮（caches 在，selectTab 不会重复请求）
            selectTab(currentTab)
        }
    }

    // MARK: - 子 Tab 栏

    private fun buildTabs() {
        binding.tabContainer.removeAllViews()
        chipViews.clear()
        visibleTabs().forEach { tab ->
            val chip = LayoutInflater.from(this)
                .inflate(R.layout.item_level_chip, binding.tabContainer, false) as TextView
            chip.text = tab.title
            chip.setOnClickListener {
                if (tab != currentTab) selectTab(tab)
            }
            binding.tabContainer.addView(chip)
            chipViews[tab] = chip
        }
    }

    private fun selectTab(tab: ClassTab, force: Boolean = false) {
        currentTab = tab

        // 选中态高亮（样式同 Cafe 分级 Tab）
        chipViews.forEach { (t, chip) ->
            val active = t == tab
            chip.setTextColor(
                ContextCompat.getColor(
                    this, if (active) R.color.white else R.color.text_secondary
                )
            )
            chip.background = ContextCompat.getDrawable(
                this, if (active) R.drawable.bg_tab_selected else R.drawable.bg_tab_normal
            )
            chip.setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
        }
        scrollChipToCenter(tab)

        val isCourse = tab == ClassTab.COURSE
        val isWebTab = isWebViewTab(tab)
        binding.recyclerView.isVisible = isCourse
        binding.tabContent.isVisible = !isCourse && !isWebTab
        binding.webContainer.isVisible = isWebTab
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = false
        // 横竖屏悬浮钮：仅教师宽表格页（对齐 iOS TeacherWideTableModifier）
        binding.btnRotate.isVisible = isTeacher && tab in wideTableTabs
        // 切到非 WebView tab：记录的返回态随隐藏容器一并失效（避免返回键
        // 拿隐藏页的 canGoBack 误 goBack）
        if (!isWebTab) {
            activeWebView?.onPause()
            activeWebView = null
        }

        // 横屏且「本页主动切成了横屏」（教师宽表格页）时，切到不支持横屏的
        // tab（Course/Class List/Assessments/Gradebook）才恢复竖屏；
        // Assignments↔Attendance 互切保持横屏（对齐 iOS onChange(selectedTab)）。
        //
        // 判据必须是「我们自己把页面锁成了横屏」(landscapeForcedByPage)，
        // 而不能用「设备当前就是横屏」(isLandscape)：
        // 平板/横屏模拟器天然为横屏时，isLandscape() 恒 true，会把本来正常的
        // 横屏强制扭成竖屏（进班级页即被转竖屏的 bug）。iOS 用的是
        // OrientationLock.landscapeOnly —— 只反映「页面主动锁横屏」，与设备
        // 自然方向无关，这里对齐该语义。
        if (forcedLandscape && !(isTeacher && tab in wideTableTabs)) {
            setOrientation(false)
        }

        when {
            isCourse -> {
                // Course 首次进入才拉取，切回直接用已有数据（旋转重建由 LiveData 重放回填）
                if (force || !viewModel.isLoaded(ClassTab.COURSE)) {
                    binding.layoutLoading.isVisible = true
                    viewModel.loadCourse(klass.id)
                }
            }
            isWebTab -> showWebViewTab(tab)
            tab == ClassTab.CLASS_LIST && isTeacher -> {
                val cached = if (teacherListTabIsCurrent) cachedTeacherCurrent else cachedTeacherRemoved
                if (cached != null) {
                    renderTeacherClassList(teacherListTabIsCurrent, cached)
                } else {
                    binding.layoutLoading.isVisible = true
                    binding.tabContentContainer.removeAllViews()
                    viewModel.loadTeacherClassList(klass.id, teacherListTabIsCurrent, force)
                }
            }
            tabHasCache(tab) -> renderCurrentTab()
            else -> {
                binding.layoutLoading.isVisible = true
                binding.tabContentContainer.removeAllViews()
                viewModel.loadTabPage(klass.id, tab, force)
            }
        }
    }

    /** 子 Tab 是否已有缓存数据（Course 恒 false，走 isLoaded 判断） */
    private fun tabHasCache(tab: ClassTab): Boolean = when (tab) {
        ClassTab.CLASS_LIST -> cachedMembers != null
        ClassTab.ASSIGNMENTS -> cachedAssignments != null
        ClassTab.CLASS_INFO -> cachedClassInfo != null
        else -> false
    }

    private fun renderCurrentTab() {
        when (currentTab) {
            ClassTab.CLASS_LIST -> renderClassList(cachedMembers ?: emptyList())
            ClassTab.ASSIGNMENTS -> renderAssignments(cachedAssignments ?: emptyList())
            ClassTab.CLASS_INFO -> renderClassInfo(cachedClassInfo ?: emptyList())
            else -> {}
        }
    }

    /** 点击 chip 后滚动居中（对齐 iOS ScrollViewReader 滚动居中） */
    private fun scrollChipToCenter(tab: ClassTab) {
        binding.tabScroll.post {
            val chip = chipViews[tab] ?: return@post
            val target = chip.left + chip.width / 2 - binding.tabScroll.width / 2
            binding.tabScroll.smoothScrollTo(target.coerceAtLeast(0), 0)
        }
    }

    // MARK: - WebView 子 Tab（学生补齐页 + 教师端承载页）

    /** 该 tab 是否 WebView 承载（学生 Attendance/Assessments 补齐 + 教师端四个页） */
    private fun isWebViewTab(tab: ClassTab): Boolean = when (tab) {
        ClassTab.ATTENDANCE, ClassTab.ASSESSMENTS, ClassTab.GRADEBOOK -> true
        ClassTab.ASSIGNMENTS -> isTeacher
        else -> false
    }

    /** 各 WebView tab 的承载参数（对齐 iOS TeacherPageWebView / 学生端补齐页） */
    private fun webViewConfigFor(tab: ClassTab): WebViewHelper.PageConfig {
        val url = com.yangfei.smrtenglish.constant.UrlConstant.fullUrl(
            com.yangfei.smrtenglish.constant.UrlConstant.classPage(klass.id, tab.path)
        )
        val hideChrome = WebViewHelper.CLASS_PAGE_HIDE_CHROME_CSS
        return when (tab) {
            ClassTab.ATTENDANCE ->
                if (isTeacher) {
                    WebViewHelper.PageConfig(
                        url = url, syncCookies = true, pageBackgroundColor = android.graphics.Color.WHITE,
                        hideSiteHeader = true,
                        extraCss = hideChrome + WebViewHelper.TEACHER_ATTENDANCE_COLUMNS_CSS +
                            WebViewHelper.TEACHER_TABLE_SHRINK_CSS + WebViewHelper.WIDE_VIEWPORT_PAN_CSS,
                        fixedViewportWidth = 1024, viewportShrinkLowerBound = 768,
                        viewportScale = 0.9, pageLoadJs = WebViewHelper.CHINESE_NAME_NORMALIZE_JS
                    )
                } else {
                    WebViewHelper.PageConfig(
                        url = url, syncCookies = true, pageBackgroundColor = android.graphics.Color.WHITE,
                        hideSiteHeader = true, extraCss = hideChrome,
                        pageLoadJs = WebViewHelper.CHINESE_NAME_NORMALIZE_JS
                    )
                }
            ClassTab.ASSIGNMENTS ->
                WebViewHelper.PageConfig(
                    url = url, syncCookies = true, pageBackgroundColor = android.graphics.Color.WHITE,
                    hideSiteHeader = true,
                    extraCss = hideChrome + WebViewHelper.TEACHER_TABLE_SHRINK_CSS +
                        WebViewHelper.WIDE_VIEWPORT_PAN_CSS,
                    fixedViewportWidth = 1024, viewportShrinkLowerBound = 992,
                    viewportScale = 0.9, pageLoadJs = WebViewHelper.CHINESE_NAME_NORMALIZE_JS,
                    // Scheduled/Shared/Complete 是同 path 的 ?tab= 真实导航：
                    // 折叠历史，否则返回键会逐级退回切换过的标签而非退出班级页
                    collapseTabHistory = true
                )
            ClassTab.ASSESSMENTS ->
                WebViewHelper.PageConfig(
                    url = url, syncCookies = true, pageBackgroundColor = android.graphics.Color.WHITE,
                    hideSiteHeader = true,
                    extraCss = hideChrome + if (isTeacher) WebViewHelper.CLASS_PAGE_HIDE_CIRCLE_BUTTONS_CSS else "",
                    pageLoadJs = WebViewHelper.CHINESE_NAME_NORMALIZE_JS
                )
            else -> // GRADEBOOK
                WebViewHelper.PageConfig(
                    url = url, syncCookies = true, pageBackgroundColor = android.graphics.Color.WHITE,
                    hideSiteHeader = true, extraCss = hideChrome,
                    pageLoadJs = WebViewHelper.CHINESE_NAME_NORMALIZE_JS
                )
        }
    }

    /** 显示/复用该 tab 的 WebView（首次创建并加载；切回保留已加载内容） */
    private fun showWebViewTab(tab: ClassTab) {
        var webView = webViews[tab]
        if (webView == null) {
            val config = webViewConfigFor(tab)
            webView = AxisLockWebView(this)
            // 宽表格页开滚动轴锁：斜向拖动只滚主导轴（对齐 iOS AxisLockScrollDelegate，
            // iOS 对固定视口页始终启用，无开关）
            webView.setAxisLockEnabled(config.fixedViewportWidth != null)
            webView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT
            )
            // index 0：WebView 垫底，顶部进度条不被盖住
            binding.webContainer.addView(webView, 0, webView.layoutParams)
            WebViewHelper.setupAndLoad(
                webView, config,
                WebViewHelper.bindProgress(binding.webProgress)
            )
            webViews[tab] = webView
        }
        webViews.forEach { (t, w) ->
            val active = t == tab
            w.isVisible = active
            if (active) w.onResume() else w.onPause()
        }
        activeWebView = webView
    }

    // MARK: - 各子 Tab 渲染（原生页）

    /**
     * 学生 Class List：成员卡网格（首字兜底圆 + 姓名），对齐 iOS
     * `LazyVGrid(columns: [GridItem(.adaptive(minimum: 150), spacing: 12)])`——
     * 按可用宽度自适应列数（360dp 手机 2 列、720dp 大屏 4 列），
     * 列间距 12dp（原先写死 2 列且无间距，与 iOS 的间距体系不一致）。
     */
    private fun renderClassList(members: List<String>) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        if (members.isEmpty()) {
            // 对齐 iOS ClassListTabView：空列表不渲染任何提示（无空态文案）
            showContentState()
            return
        }
        val inflater = LayoutInflater.from(this)
        val columns = memberGridColumns()
        val gap = dp(this, MEMBER_GRID_GAP_DP.toFloat())

        members.chunked(columns).forEach { rowMembers ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowMembers.forEachIndexed { index, name ->
                val item = ItemClassTabMemberBinding.inflate(inflater, row, false)
                item.tvInitial.text = name.take(1)
                item.tvName.text = name
                val params = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                // 除本行最后一张卡外，右侧留出列间距；末张不留，避免右边缘多出空隙
                if (index != rowMembers.lastIndex) params.marginEnd = gap
                row.addView(item.root, params)
            }
            // 补空位保证与其它行对齐（占位同样留出列间距）
            if (rowMembers.size < columns) {
                val params = LinearLayout.LayoutParams(0, 1, (columns - rowMembers.size).toFloat())
                params.marginStart = gap
                row.addView(Space(this), params)
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = gap }
            )
        }
        showContentState()
    }

    /**
     * 成员网格列数：对齐 iOS `GridItem(.adaptive(minimum: 150), spacing: 12)`，
     * 复用 [UiUtils.adaptiveColumns]（与 ClassesFragment 班级卡内网格同一算法）。
     * 容器左右各 16dp padding。
     */
    private fun memberGridColumns(): Int = UiUtils.adaptiveColumns(
        availableWidthDp = resources.configuration.screenWidthDp - 32,
        minItemWidthDp = MEMBER_GRID_MIN_DP,
        spacingDp = MEMBER_GRID_GAP_DP
    )

    /** 学生 Assignments：作业行卡片，有课时链接的可点跳原生课时详情 */
    private fun renderAssignments(items: List<ClassAssignment>) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(emptyTextView(getString(R.string.classes_assignments_empty)))
            showContentState()
            return
        }
        val inflater = LayoutInflater.from(this)
        items.forEach { assignment ->
            val item = ItemClassAssignmentBinding.inflate(inflater, container, false)

            // 状态：Complete 绿色对勾，其余灰色时钟（对齐 iOS 图标逻辑）
            val complete = assignment.status.lowercase().contains("complete")
            item.ivStatus.setImageResource(
                if (complete) R.drawable.ic_check_circle else R.drawable.ic_clock_alert
            )
            item.ivStatus.setColorFilter(
                ContextCompat.getColor(this, if (complete) R.color.green else R.color.text_secondary)
            )
            item.ivStatus.alpha = if (complete) 1f else 0.6f

            item.tvTitle.text = assignment.title
            item.tvSection.isVisible = assignment.section.isNotEmpty()
            item.tvSection.text = assignment.section
            item.tvDue.isVisible = assignment.dueDate.isNotEmpty()
            item.tvDue.text = "Due ${assignment.dueDate}"

            item.tvScore.isVisible = assignment.score.isNotEmpty()
            item.tvScore.text = assignment.score
            item.tvScore.setTextColor(
                ContextCompat.getColor(
                    this, if (assignment.score == "100%") R.color.green else R.color.accent
                )
            )
            // chevron 恒显（对齐 iOS assignmentRow：无条件渲染 chevron，
            // 与是否有课时链接无关——原先仅在 lessonPath != null 时显示）
            item.ivChevron.isVisible = true

            // 课时链接 → 原生课时详情（对齐 iOS NavigationLink）
            parseLessonPath(assignment.lessonPath)?.let { (courseId, lessonId) ->
                item.root.setOnClickListener { openAssignmentLesson(courseId, lessonId, assignment) }
            }
            container.addView(item.root)
        }
        showContentState()
    }

    /** Class Info：label + value 键值对卡片 */
    private fun renderClassInfo(items: List<ClassInfoItem>) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        // 对齐 iOS ClassInfoTabView：空列表不渲染提示（无空态文案）
        if (items.isEmpty()) {
            showContentState()
            return
        }
        val inflater = LayoutInflater.from(this)
        items.forEach { info ->
            val item = ItemClassInfoBinding.inflate(inflater, container, false)
            item.tvLabel.text = info.label
            item.tvValue.text = info.value
            container.addView(item.root)
        }
        showContentState()
    }

    // MARK: - 教师端 Class List（原生列表，对齐 iOS TeacherClassListTabView）

    /** 下划线双 tab（Current 灰色人数 badge 对齐官网默认 smrt-badge 样式） */
    private fun renderTeacherClassList(isCurrent: Boolean, members: List<TeacherClassListMember>) {
        val container = binding.tabContentContainer
        container.removeAllViews()

        // 双 tab 行（下划线选中态，对齐 iOS TeacherClassListTabView.tabBar）
        val tabRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        listOf(true, false).forEachIndexed { index, isCurrentTab ->
            val active = isCurrentTab == isCurrent
            val column = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(this@ClassCourseActivity, 10f), 0, dp(this@ClassCourseActivity, 6f))
            }
            val labelRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            labelRow.addView(TextView(this).apply {
                text = getString(
                    if (isCurrentTab) R.string.teacher_list_current else R.string.teacher_list_removed
                )
                textSize = 14f
                setTextColor(
                    ContextCompat.getColor(
                        this@ClassCourseActivity,
                        if (active) R.color.text_primary else R.color.text_secondary
                    )
                )
                setTypeface(null, if (active) Typeface.BOLD else Typeface.NORMAL)
            })
            if (isCurrentTab && teacherCurrentCount > 0) {
                labelRow.addView(TextView(this).apply {
                    text = teacherCurrentCount.toString()
                    textSize = 10f
                    setTextColor(ContextCompat.getColor(this@ClassCourseActivity, R.color.white))
                    background = android.graphics.drawable.GradientDrawable().apply {
                        setColor(0x8C808080.toInt())
                        cornerRadius = dp(context, 8f).toFloat()
                    }
                    setPadding(dp(context, 5f), dp(context, 1f), dp(context, 5f), dp(context, 1f))
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { marginStart = dp(context, 5f); gravity = Gravity.CENTER_VERTICAL }
                })
            }
            column.addView(labelRow)
            // 下划线宽度 = label 实测宽度。不能写 MATCH_PARENT：外层 column 是
            // wrap_content，MATCH_PARENT 子 View 会把列撑满整行剩余宽度，
            // 把第二个 tab（Removed）挤出屏幕外
            labelRow.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
            column.addView(View(this).apply {
                // 注意：Color.TRANSPARENT 是颜色值不是资源 ID，
                // 不能传给 ContextCompat.getColor（Resources$NotFoundException: ID #0x0）
                setBackgroundColor(
                    if (active) ContextCompat.getColor(this@ClassCourseActivity, R.color.accent)
                    else android.graphics.Color.TRANSPARENT
                )
            }, LinearLayout.LayoutParams(
                labelRow.measuredWidth, dp(this@ClassCourseActivity, 2.5f)
            ))
            column.setOnClickListener {
                if (teacherListTabIsCurrent != isCurrentTab) {
                    teacherListTabIsCurrent = isCurrentTab
                    selectTab(ClassTab.CLASS_LIST)
                }
            }
            tabRow.addView(column)
            if (index == 0) {
                tabRow.addView(
                    View(this),
                    LinearLayout.LayoutParams(dp(this@ClassCourseActivity, 22f), 1)
                )
            }
        }
        container.addView(
            tabRow,
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(this@ClassCourseActivity, 4f) }
        )

        if (members.isEmpty()) {
            container.addView(
                emptyTextView(
                    getString(
                        if (isCurrent) R.string.teacher_students_empty
                        else R.string.teacher_removed_students_empty
                    )
                )
            )
            showContentState()
            return
        }

        val inflater = LayoutInflater.from(this)
        members.forEach { member ->
            val item = ItemTeacherMemberBinding.inflate(inflater, container, false)
            item.tvInitial.text = member.name.take(1)
            item.ivAvatar.loadCircle(member.avatarURL)
            item.tvName.text = member.name
            item.tvNumber.isVisible = member.number.isNotEmpty()
            item.tvNumber.text = "Id: ${member.number}"
            item.tvEmail.isVisible = member.email.isNotEmpty()
            item.tvEmail.text = member.email
            container.addView(
                item.root,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(this@ClassCourseActivity, 10f) }
            )
        }
        showContentState()
    }

    // MARK: - 返回与横竖屏

    /**
     * 返回按钮统一动作（对齐 iOS handleBack）：当前 tab 的 WebView 有内部
     * 浏览历史（如教师 Assignments 行点 Class Results 跳出的子页）→ 先 goBack
     * 逐级返回；无历史才退出班级页（横屏先恢复竖屏）。
     */
    private fun handleBack() {
        val webView = activeWebView
        if (webView != null && webView.canGoBack()) {
            webView.goBack()
            return
        }
        // 只有本页主动锁过横屏才需要恢复竖屏；设备天然横屏（平板）不该被扭回
        if (forcedLandscape) setOrientation(false)
        finish()
    }

    private fun isLandscape(): Boolean =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    /** 页面级方向切换（对齐 iOS setInterfaceOrientation：requestGeometryUpdate 立即旋转） */
    private fun setOrientation(landscape: Boolean) {
        requestedOrientation = if (landscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    /** 横屏：紧凑顶栏（TitleBar 隐藏，返回钮与 tab 合并一行）；竖屏：TitleBar 接管 */
    private fun updateTopBarForOrientation() {
        val landscape = isLandscape()
        binding.topBarView.isVisible = !landscape
        binding.btnBackCompact.isVisible = landscape
        binding.btnRotate.rotation = if (landscape) 90f else 0f
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateTopBarForOrientation()
    }

    // MARK: - 跳转

    private fun openLesson(lesson: ClassLesson) {
        startActivity(Intent(this, LessonDetailActivity::class.java).apply {
            putExtra(
                LessonDetailActivity.EXTRA_COURSE,
                LibraryCourse(id = courseLibraryId, title = klass.name)
            )
            putExtra(
                LessonDetailActivity.EXTRA_LESSON,
                LessonItem(
                    id = lesson.id,
                    courseId = lesson.courseId,
                    number = lesson.number,
                    title = lesson.title
                )
            )
        })
    }

    private fun openAssignmentLesson(courseId: String, lessonId: String, assignment: ClassAssignment) {
        startActivity(Intent(this, LessonDetailActivity::class.java).apply {
            putExtra(
                LessonDetailActivity.EXTRA_COURSE,
                LibraryCourse(id = courseId, title = "")
            )
            putExtra(
                LessonDetailActivity.EXTRA_LESSON,
                LessonItem(
                    id = lessonId,
                    courseId = courseId,
                    number = "",
                    title = assignment.title
                )
            )
        })
    }

    /** 解析 lessonPath 中的 lesson/{courseId}/{lessonId} */
    private fun parseLessonPath(path: String?): Pair<String, String>? {
        if (path == null) return null
        val m = Regex("lesson/(\\d+)/(\\d+)").find(path) ?: return null
        val values = m.groupValues
        return if (values.size == 3) Pair(values[1], values[2]) else null
    }

    // MARK: - 生命周期

    override fun onPause() {
        activeWebView?.onPause()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        activeWebView?.onResume()
        updateTopBarForOrientation()
    }

    override fun onDestroy() {
        // 离开班级页时仅撤销「本页主动锁的横屏」（对齐 iOS onDisappear 的
        // orientationLock.landscapeOnly 判断）；设备天然横屏不动，
        // 否则平板上退出班级页会把整个 App 扭成竖屏
        if (forcedLandscape) setOrientation(false)
        webViews.values.forEach {
            it.stopLoading()
            it.destroy()
        }
        webViews.clear()
        activeWebView = null
        super.onDestroy()
    }

    // MARK: - 状态

    private fun showContentState() {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = false
    }

    /**
     * 显示失败态。带 [tab] 时只有该 tab 仍是当前 tab 才展示——避免
     * 后台失败（如切走后才回来的请求）把错误页盖到别的 tab 上。
     */
    private fun showTabError(message: String?, tab: ClassTab? = null) {
        if (tab != null && tab != currentTab) return
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvError.text = message ?: getString(R.string.error_bad_response)
    }

    /**
     * 重试当前 tab（对齐 iOS ClassTabPageContainer 的重试按钮）：
     * - WebView 承载页：重新加载当前 URL（清掉 activeWebView 的返回态不必要，
     *   reload 后 didFinish 会刷新）；原先直接走 selectTab 对 WebView 是空操作
     * - 原生页：强制重新拉取
     */
    private fun retryCurrentTab() {
        val tab = currentTab
        val webView = webViews[tab]
        if (isWebViewTab(tab) && webView != null) {
            binding.layoutError.isVisible = false
            binding.layoutLoading.isVisible = false
            webView.reload()
            return
        }
        selectTab(tab, force = true)
    }

    /** 居中的空态提示（对齐 iOS emptyState） */
    private fun emptyTextView(text: String): TextView {
        val tv = TextView(this).apply {
            this.text = text
            textSize = 14f
            setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            gravity = android.view.Gravity.CENTER
            setPadding(0, dp(context, 40f), 0, 0)
        }
        return tv
    }

    companion object {
        const val EXTRA_CLASS = "extra_class"

        /** 学生 Class List 网格列间距（对齐 iOS LazyVGrid spacing: 12） */
        private const val MEMBER_GRID_GAP_DP = 12

        /** 学生 Class List 最小列宽（对齐 iOS GridItem(.adaptive(minimum: 150))） */
        private const val MEMBER_GRID_MIN_DP = 150
    }
}
