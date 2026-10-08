package com.yangfei.smrtenglish.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import androidx.core.view.isVisible
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.databinding.ViewTitleBarBinding

/**
 * 通用标题栏（对齐 iOS 的 inline navigation bar：返回箭头 + 居中标题 + 右侧文字）
 */
class TitleBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding: ViewTitleBarBinding = ViewTitleBarBinding.inflate(LayoutInflater.from(context), this)

    init {
        binding.titleBarBack.setOnClickListener {
            if (onBackClickListener != null) {
                onBackClickListener!!.invoke()
            } else {
                (context as? androidx.activity.ComponentActivity)?.onBackPressedDispatcher?.onBackPressed()
            }
        }
    }

    private var onBackClickListener: (() -> Unit)? = null

    fun setTitle(title: CharSequence?) {
        binding.titleBarTitle.text = title ?: ""
    }

    fun setRightText(text: CharSequence?, onClick: (() -> Unit)? = null) {
        binding.titleBarRight.isVisible = !text.isNullOrEmpty()
        binding.titleBarRight.text = text ?: ""
        if (onClick != null) {
            binding.titleBarRight.setOnClickListener { onClick.invoke() }
        }
    }

    fun setOnBackClickListener(listener: () -> Unit) {
        onBackClickListener = listener
    }

    fun hideBack() {
        binding.titleBarBack.isVisible = false
    }

    fun showDivider(show: Boolean) {
        val divider = findViewById<android.view.View>(R.id.title_bar_divider)
        divider?.isVisible = show
    }
}
