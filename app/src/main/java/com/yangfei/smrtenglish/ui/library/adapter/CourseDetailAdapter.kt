package com.yangfei.smrtenglish.ui.library.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.yangfei.smrtenglish.bean.CourseUnit
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.databinding.ItemCourseBannerBinding
import com.yangfei.smrtenglish.databinding.ItemCourseUnitHeaderBinding
import com.yangfei.smrtenglish.databinding.ItemLessonBinding
import com.yangfei.smrtenglish.databinding.ItemSectionTitleBinding
import com.yangfei.smrtenglish.util.loadUrl

/**
 * 课程详情列表适配器（对齐 iOS CourseDetailView）
 *
 * 扁平化多类型结构：封面横幅 → 单元头 → 分组标题 → 课时行。
 * 相比嵌套 RecyclerView，扁平结构滚动性能更好，也不会出现高度测量问题。
 */
class CourseDetailAdapter(
    private val course: LibraryCourse,
    private val onLessonClick: (LessonItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class Banner(val course: LibraryCourse) : Row()
        data class UnitHeader(val unit: CourseUnit) : Row()
        data class Section(val title: String) : Row()
        data class Lesson(val lesson: LessonItem) : Row()
    }

    private val rows = ArrayList<Row>()

    fun submit(units: List<CourseUnit>) {
        rows.clear()
        rows.add(Row.Banner(course))
        units.forEach { unit ->
            rows.add(Row.UnitHeader(unit))
            unit.sections.forEach { section ->
                rows.add(Row.Section(section.title))
                section.lessons.forEach { rows.add(Row.Lesson(it)) }
            }
        }
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.Banner -> TYPE_BANNER
        is Row.UnitHeader -> TYPE_UNIT
        is Row.Section -> TYPE_SECTION
        is Row.Lesson -> TYPE_LESSON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_BANNER -> BannerHolder(ItemCourseBannerBinding.inflate(inflater, parent, false))
            TYPE_UNIT -> UnitHolder(ItemCourseUnitHeaderBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(ItemSectionTitleBinding.inflate(inflater, parent, false))
            else -> LessonHolder(ItemLessonBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.Banner -> (holder as BannerHolder).bind(row.course)
            is Row.UnitHeader -> (holder as UnitHolder).bind(row.unit)
            is Row.Section -> (holder as SectionHolder).bind(row.title)
            is Row.Lesson -> (holder as LessonHolder).bind(row.lesson)
        }
    }

    class BannerHolder(private val binding: ItemCourseBannerBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(course: LibraryCourse) {
            binding.ivImage.loadUrl(course.imageURL)
            binding.tvTitle.text = course.title
            binding.tvCode.isVisible = course.code.isNotEmpty()
            binding.tvCode.text = course.code
            binding.tvDesc.isVisible = course.courseDescription.isNotEmpty()
            binding.tvDesc.text = course.courseDescription
        }
    }

    class UnitHolder(private val binding: ItemCourseUnitHeaderBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(unit: CourseUnit) {
            binding.ivImage.loadUrl(unit.imageURL)
            binding.tvName.text = unit.name
            binding.tvNumber.text = "Unit ${unit.number}"
        }
    }

    class SectionHolder(private val binding: ItemSectionTitleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvSectionTitle.text = title
        }
    }

    inner class LessonHolder(private val binding: ItemLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(lesson: LessonItem) {
            binding.tvNumber.text = lesson.number
            binding.tvTitle.text = lesson.title
            val locked = lesson.locked
            binding.tvNumber.alpha = if (locked) 0.5f else 1f
            binding.tvTitle.alpha = if (locked) 0.5f else 1f
            binding.tvStatus.isVisible = false
            binding.ivStatus.visibility = View.VISIBLE
            binding.ivStatus.setImageResource(
                if (locked) com.yangfei.smrtenglish.R.drawable.ic_lock
                else com.yangfei.smrtenglish.R.drawable.ic_chevron_right
            )
            binding.root.setOnClickListener {
                if (!locked) onLessonClick(lesson)
            }
        }
    }

    companion object {
        private const val TYPE_BANNER = 0
        private const val TYPE_UNIT = 1
        private const val TYPE_SECTION = 2
        private const val TYPE_LESSON = 3
    }
}
