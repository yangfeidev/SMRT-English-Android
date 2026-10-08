package com.yangfei.smrtenglish.ui.library.adapter

import androidx.core.view.isVisible
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.databinding.ItemCourseBinding
import com.yangfei.smrtenglish.util.loadUrl
import com.yangfei.smrtenglish.view.recyclerview.adapter.BindingBaseAdapter

/**
 * 课程卡片适配器（对齐 iOS CourseCard）
 */
class CourseAdapter(
    private val onCourseClick: (LibraryCourse) -> Unit
) : BindingBaseAdapter<LibraryCourse, ItemCourseBinding>() {

    override fun bindingConvert(holder: VBViewHolder<ItemCourseBinding>, item: LibraryCourse?) {
        item ?: return
        val binding = holder.vb
        binding.ivImage.loadUrl(item.imageURL)
        binding.tvTitle.text = item.title
        binding.tvDesc.isVisible = item.courseDescription.isNotEmpty()
        binding.tvDesc.text = item.courseDescription
        binding.tvCode.isVisible = item.code.isNotEmpty()
        binding.tvCode.text = item.code
        binding.root.setOnClickListener { onCourseClick(item) }
    }
}
