package com.yangfei.smrtenglish.ui.classes.adapter

import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.bean.ClassLesson
import com.yangfei.smrtenglish.bean.ClassUnit
import com.yangfei.smrtenglish.databinding.ItemClassLessonBinding
import com.yangfei.smrtenglish.databinding.ItemClassUnitBinding
import com.yangfei.smrtenglish.databinding.ItemSectionTitleBinding
import com.yangfei.smrtenglish.util.loadUrl

/**
 * 班级课程页单元树适配器（对齐 iOS ClassCourseView 手风琴）
 *
 * 扁平化多类型结构：单元头（可折叠）→ 分组标题 → 课时行。
 */
class ClassCourseAdapter(
    private val onLessonClick: (ClassLesson) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    sealed class Row {
        data class UnitHeader(val unit: ClassUnit, val expanded: Boolean) : Row()
        data class Section(val title: String) : Row()
        data class Lesson(val lesson: ClassLesson) : Row()
    }

    private var units: List<ClassUnit> = emptyList()
    private val expandedIds = HashSet<String>()
    private val rows = ArrayList<Row>()

    fun submit(newUnits: List<ClassUnit>) {
        units = newUnits
        expandedIds.clear()
        // 默认展开第一个单元（对齐官网 accordion 行为）
        newUnits.firstOrNull()?.let { expandedIds.add(it.id) }
        rebuild()
    }

    private fun rebuild() {
        rows.clear()
        units.forEach { unit ->
            val expanded = expandedIds.contains(unit.id)
            rows.add(Row.UnitHeader(unit, expanded))
            if (!expanded) return@forEach
            unit.sections.forEach { section ->
                rows.add(Row.Section(section.title))
                section.lessons.forEach { rows.add(Row.Lesson(it)) }
            }
        }
        notifyDataSetChanged()
    }

    private fun toggle(unitId: String) {
        if (expandedIds.contains(unitId)) expandedIds.remove(unitId) else expandedIds.add(unitId)
        rebuild()
    }

    override fun getItemCount(): Int = rows.size

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is Row.UnitHeader -> TYPE_UNIT
        is Row.Section -> TYPE_SECTION
        is Row.Lesson -> TYPE_LESSON
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_UNIT -> UnitHolder(ItemClassUnitBinding.inflate(inflater, parent, false))
            TYPE_SECTION -> SectionHolder(ItemSectionTitleBinding.inflate(inflater, parent, false))
            else -> LessonHolder(ItemClassLessonBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is Row.UnitHeader -> (holder as UnitHolder).bind(row.unit, row.expanded)
            is Row.Section -> (holder as SectionHolder).bind(row.title)
            is Row.Lesson -> (holder as LessonHolder).bind(row.lesson)
        }
    }

    inner class UnitHolder(private val binding: ItemClassUnitBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(unit: ClassUnit, expanded: Boolean) {
            binding.ivImage.loadUrl(unit.imageURL)
            binding.tvName.text = unit.name
            binding.tvNumber.text = unit.number
            binding.ivArrow.setImageResource(
                if (expanded) R.drawable.ic_chevron_up else R.drawable.ic_chevron_down
            )
            binding.root.setOnClickListener { toggle(unit.id) }
        }
    }

    class SectionHolder(private val binding: ItemSectionTitleBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(title: String) {
            binding.tvSectionTitle.text = title
        }
    }

    inner class LessonHolder(private val binding: ItemClassLessonBinding) :
        RecyclerView.ViewHolder(binding.root) {
        fun bind(lesson: ClassLesson) {
            val context = binding.root.context
            binding.tvNumber.text = lesson.number
            binding.tvTitle.text = lesson.title

            val completed = lesson.status.lowercase().contains("complete")
            if (completed) {
                binding.ivStatus.setImageResource(R.drawable.ic_check_circle)
                binding.tvStatus.isVisible = true
                binding.tvStatus.text = lesson.status
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.green))
            } else {
                binding.ivStatus.setImageResource(R.drawable.ic_dot)
                tintDot(
                    binding.ivStatus.drawable,
                    when {
                        lesson.hasExercises -> ContextCompat.getColor(context, R.color.accent)
                        lesson.status == "Not shared" -> ContextCompat.getColor(context, R.color.text_secondary) and 0x00FFFFFF or 0x59000000
                        else -> ContextCompat.getColor(context, R.color.text_secondary) and 0x00FFFFFF or 0x26000000
                    }
                )
                binding.tvStatus.isVisible = lesson.status.isNotEmpty()
                binding.tvStatus.text = lesson.status
                binding.tvStatus.setTextColor(ContextCompat.getColor(context, R.color.text_secondary))
            }

            binding.root.setOnClickListener { onLessonClick(lesson) }
        }

        private fun tintDot(drawable: Drawable?, color: Int) {
            drawable?.mutate()?.setColorFilter(color, PorterDuff.Mode.SRC_IN)
        }
    }

    companion object {
        private const val TYPE_UNIT = 0
        private const val TYPE_SECTION = 1
        private const val TYPE_LESSON = 2
    }
}
