package com.yangfei.smrtenglish.ui.classes

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import android.widget.Space
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.ClassAssignment
import com.yangfei.smrtenglish.bean.ClassInfoItem
import com.yangfei.smrtenglish.bean.ClassLesson
import com.yangfei.smrtenglish.bean.ClassTab
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.databinding.ActivityClassCourseBinding
import com.yangfei.smrtenglish.databinding.ItemClassAssignmentBinding
import com.yangfei.smrtenglish.databinding.ItemClassInfoBinding
import com.yangfei.smrtenglish.databinding.ItemClassTabMemberBinding
import com.yangfei.smrtenglish.ui.classes.adapter.ClassCourseAdapter
import com.yangfei.smrtenglish.ui.library.LessonDetailActivity
import com.yangfei.smrtenglish.util.UiUtils.dp

/**
 * 班级课程页（对齐 iOS ClassCourseView + ClassCourseTabViews）
 *
 * 6 个子 Tab：Course 保持原生单元树；其余 5 个 Tab 原生解析渲染
 * （成员列表 / 作业列表可跳课时详情 / 考勤与测评空态 / 班级信息键值对），
 * 各 Tab 首次切到时才拉取（懒加载），切回使用缓存不再重复请求。
 */
class ClassCourseActivity : BaseActivity<ActivityClassCourseBinding, ClassCourseViewModel>() {

    private lateinit var adapter: ClassCourseAdapter

    private val klass: StudentClass by lazy {
        intent.getParcelableExtra(EXTRA_CLASS) ?: StudentClass()
    }

    private var courseLibraryId: String = ""

    private var currentTab = ClassTab.COURSE

    /** Tab chip 缓存（选中高亮 + 点击滚动居中） */
    private val chipViews = HashMap<ClassTab, TextView>()

    // 子 Tab 数据缓存（旋转重建后由 LiveData 重放回填）
    private var cachedMembers: List<String>? = null
    private var cachedAssignments: List<ClassAssignment>? = null
    private var cachedAttendance: Boolean? = null
    private var cachedAssessments: Boolean? = null
    private var cachedClassInfo: List<ClassInfoItem>? = null

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar { it.setTitle(klass.name) }

        adapter = ClassCourseAdapter { lesson -> openLesson(lesson) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        buildTabs()
        binding.btnRetry.setOnClickListener { selectTab(currentTab, force = true) }
    }

    override fun loadData() {
        selectTab(ClassTab.COURSE)
    }

    override fun initObserver() {
        viewModel.courseResult.observe(this) { response ->
            if (response.isSuccessful) {
                val course = response.data
                if (course == null || course.units.isEmpty()) {
                    binding.layoutLoading.isVisible = false
                    binding.layoutError.isVisible = true
                    binding.tvError.text = getString(R.string.classes_no_course)
                    return@observe
                }
                courseLibraryId = course.courseLibraryId
                requireTitleBar { it.setTitle(course.courseName.ifEmpty { klass.name }) }
                adapter.submit(course.units)
                showContentState()
            } else {
                showTabError(response.message ?: getString(R.string.error_bad_response))
            }
        }

        viewModel.classListResult.observe(this) { response ->
            if (!response.isSuccessful) return@observe showTabError(response.message)
            cachedMembers = response.data ?: emptyList()
            if (currentTab == ClassTab.CLASS_LIST) renderClassList(cachedMembers!!)
        }
        viewModel.assignmentsResult.observe(this) { response ->
            if (!response.isSuccessful) return@observe showTabError(response.message)
            cachedAssignments = response.data ?: emptyList()
            if (currentTab == ClassTab.ASSIGNMENTS) renderAssignments(cachedAssignments!!)
        }
        viewModel.attendanceResult.observe(this) { response ->
            if (!response.isSuccessful) return@observe showTabError(response.message)
            cachedAttendance = response.data
            if (currentTab == ClassTab.ATTENDANCE) renderAttendance(cachedAttendance == true)
        }
        viewModel.assessmentsResult.observe(this) { response ->
            if (!response.isSuccessful) return@observe showTabError(response.message)
            cachedAssessments = response.data
            if (currentTab == ClassTab.ASSESSMENTS) renderAssessments(cachedAssessments == true)
        }
        viewModel.classInfoResult.observe(this) { response ->
            if (!response.isSuccessful) return@observe showTabError(response.message)
            cachedClassInfo = response.data ?: emptyList()
            if (currentTab == ClassTab.CLASS_INFO) renderClassInfo(cachedClassInfo!!)
        }
    }

    // MARK: - 子 Tab 栏

    private fun buildTabs() {
        binding.tabContainer.removeAllViews()
        chipViews.clear()
        ClassTab.entries.forEach { tab ->
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

        // Course = 单元树 RecyclerView；其余走动态内容容器
        val isCourse = tab == ClassTab.COURSE
        binding.recyclerView.isVisible = isCourse
        binding.tabContent.isVisible = !isCourse
        binding.layoutError.isVisible = false

        if (isCourse) {
            // Course 首次进入才拉取，切回直接用已有数据（旋转重建由 LiveData 重放回填）
            if (force || !viewModel.isLoaded(ClassTab.COURSE)) {
                binding.layoutLoading.isVisible = true
                viewModel.loadCourse(klass.id)
            }
            return
        }

        // 已有缓存直接渲染，否则懒加载（对齐 iOS 子 Tab .task 首次拉取）
        if (tabHasCache(tab)) {
            renderCurrentTab()
        } else {
            binding.layoutLoading.isVisible = true
            binding.tabContentContainer.removeAllViews()
            viewModel.loadTabPage(klass.id, tab, force)
        }
    }

    /** 子 Tab 是否已有缓存数据（Course 恒 false，走 isLoaded 判断） */
    private fun tabHasCache(tab: ClassTab): Boolean = when (tab) {
        ClassTab.CLASS_LIST -> cachedMembers != null
        ClassTab.ASSIGNMENTS -> cachedAssignments != null
        ClassTab.ATTENDANCE -> cachedAttendance != null
        ClassTab.ASSESSMENTS -> cachedAssessments != null
        ClassTab.CLASS_INFO -> cachedClassInfo != null
        ClassTab.COURSE -> false
    }

    private fun renderCurrentTab() {
        when (currentTab) {
            ClassTab.CLASS_LIST -> renderClassList(cachedMembers ?: emptyList())
            ClassTab.ASSIGNMENTS -> renderAssignments(cachedAssignments ?: emptyList())
            ClassTab.ATTENDANCE -> renderAttendance(cachedAttendance == true)
            ClassTab.ASSESSMENTS -> renderAssessments(cachedAssessments == true)
            ClassTab.CLASS_INFO -> renderClassInfo(cachedClassInfo ?: emptyList())
            ClassTab.COURSE -> {}
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

    // MARK: - 各子 Tab 渲染

    /** Class List：每行 2 列成员卡（首字兜底圆 + 姓名），对齐 iOS LazyVGrid adaptive(150) */
    private fun renderClassList(members: List<String>) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        if (members.isEmpty()) {
            container.addView(emptyTextView(getString(R.string.classes_members_empty)))
            showContentState()
            return
        }
        val inflater = LayoutInflater.from(this)
        members.chunked(2).forEach { rowMembers ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            rowMembers.forEach { name ->
                val item = ItemClassTabMemberBinding.inflate(inflater, row, false)
                item.tvInitial.text = name.take(1)
                item.tvName.text = name
                row.addView(item.root)
            }
            // 补空位保证两列对齐
            if (rowMembers.size == 1) {
                row.addView(
                    Space(this),
                    LinearLayout.LayoutParams(0, 1, 1f)
                )
            }
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { bottomMargin = dp(this@ClassCourseActivity, 12f) }
            )
        }
        showContentState()
    }

    /** Assignments：作业行卡片，有课时链接的可点跳原生课时详情 */
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
            item.ivChevron.isVisible = assignment.lessonPath != null

            // 课时链接 → 原生课时详情（对齐 iOS NavigationLink）
            parseLessonPath(assignment.lessonPath)?.let { (courseId, lessonId) ->
                item.root.setOnClickListener { openAssignmentLesson(courseId, lessonId, assignment) }
            }
            container.addView(item.root)
        }
        showContentState()
    }

    private fun renderAttendance(hasRecords: Boolean) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        container.addView(
            if (hasRecords) {
                emptyTextView(getString(R.string.classes_attendance_web))
            } else {
                emptyTextView(getString(R.string.classes_attendance_empty))
            }
        )
        showContentState()
    }

    private fun renderAssessments(hasResults: Boolean) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        container.addView(
            if (hasResults) {
                emptyTextView(getString(R.string.classes_assessments_web))
            } else {
                emptyTextView(getString(R.string.classes_assessments_empty))
            }
        )
        showContentState()
    }

    /** Class Info：label + value 键值对卡片 */
    private fun renderClassInfo(items: List<ClassInfoItem>) {
        val container = binding.tabContentContainer
        container.removeAllViews()
        if (items.isEmpty()) {
            container.addView(emptyTextView(getString(R.string.classes_class_info_empty)))
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

    // MARK: - 状态

    private fun showContentState() {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = false
    }

    private fun showTabError(message: String?) {
        binding.layoutLoading.isVisible = false
        binding.layoutError.isVisible = true
        binding.tvError.text = message ?: getString(R.string.error_bad_response)
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
    }
}
