package com.yangfei.smrtenglish.ui.cafe.adapter

import com.yangfei.smrtenglish.bean.CafeCategory
import com.yangfei.smrtenglish.bean.CafeVideo
import com.yangfei.smrtenglish.databinding.ItemCafeVideoBinding
import com.yangfei.smrtenglish.util.UiUtils
import com.yangfei.smrtenglish.util.loadUrl
import com.yangfei.smrtenglish.view.recyclerview.adapter.BindingBaseAdapter

/**
 * Cafe 视频卡片适配器（对齐 iOS CafeVideoCard）
 */
class CafeVideoAdapter(
    private val onVideoClick: (CafeVideo) -> Unit,
    private val onCategoryClick: (CafeCategory) -> Unit
) : BindingBaseAdapter<CafeVideo, ItemCafeVideoBinding>() {

    override fun bindingConvert(holder: VBViewHolder<ItemCafeVideoBinding>, item: CafeVideo?) {
        item ?: return
        val binding = holder.vb
        val context = binding.root.context

        binding.ivThumb.loadUrl(item.thumbnailURL)
        binding.tvTitle.text = item.title

        binding.categoryContainer.removeAllViews()
        item.categories.forEach { category ->
            // 间距 6dp 对齐 iOS HStack(spacing: 6)
            val chip = UiUtils.createCategoryChip(context, category.name, category.level, CATEGORY_GAP_DP)
            chip.setOnClickListener { onCategoryClick(category) }
            binding.categoryContainer.addView(chip)
        }

        binding.root.setOnClickListener { onVideoClick(item) }
        binding.ivThumb.setOnClickListener { onVideoClick(item) }
    }

    companion object {
        /** 分类标签间距（对齐 iOS CafeView 的 HStack spacing: 6） */
        private const val CATEGORY_GAP_DP = 6f
    }
}
