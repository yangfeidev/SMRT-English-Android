package com.yangfei.smrtenglish.ui.classes.adapter

import androidx.core.view.isVisible
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.databinding.ItemClassBinding
import com.yangfei.smrtenglish.ui.classes.MemberRenderer
import com.yangfei.smrtenglish.util.UiUtils
import com.yangfei.smrtenglish.view.recyclerview.adapter.BindingBaseAdapter

/**
 * 班级卡片适配器（对齐 iOS ClassesView.classCard）
 */
class ClassAdapter(
    private val myDisplayNameProvider: () -> String?,
    private val onClassClick: (StudentClass) -> Unit
) : BindingBaseAdapter<StudentClass, ItemClassBinding>() {

    override fun bindingConvert(holder: VBViewHolder<ItemClassBinding>, item: StudentClass?) {
        item ?: return
        val binding = holder.vb
        binding.tvClassName.text = item.name
        binding.tvCourse.isVisible = item.course.isNotEmpty()
        binding.tvCourse.text = item.course
        // 列数随屏幕自适应（对齐 iOS GridItem(.adaptive(minimum: 64))）
        MemberRenderer.render(
            binding.memberContainer,
            item.members,
            myDisplayNameProvider(),
            UiUtils.memberGridColumns(binding.root.context)
        )
        binding.root.setOnClickListener { onClassClick(item) }
    }
}
