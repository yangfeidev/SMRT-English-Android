package com.yangfei.smrtenglish.util

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.yangfei.smrtenglish.R

/**
 * 通用 UI 小工具（胶囊标签 / 圆角背景 / 级别配色）
 */
object UiUtils {

    fun dp(context: Context, value: Float): Int =
        (value * context.resources.displayMetrics.density + 0.5f).toInt()

    fun roundedDrawable(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            setColor(color)
            cornerRadius = radiusPx
        }

    // ===================== 屏幕适配（对齐 iOS horizontalSizeClass 的 regular/compact） =====================

    /**
     * 是否大屏（平板 / 横屏宽屏）。
     * 对齐 iOS 的 `horizontalSizeClass == .regular`：以最短边判定，
     * 因此手机横屏仍算紧凑屏（与 iOS iPhone 横屏行为一致），不会变成多列。
     */
    fun isRegularWidth(context: Context): Boolean =
        context.resources.configuration.smallestScreenWidthDp >= 600

    /** Cafe 网格列数：紧凑 2 列，大屏 4 列（对齐 iOS CafeView） */
    fun cafeSpanCount(context: Context): Int = if (isRegularWidth(context)) 4 else 2

    /** 课程列表列数：紧凑 2 列，大屏 3 列（对齐 iOS CourseListScreen） */
    fun courseSpanCount(context: Context): Int = if (isRegularWidth(context)) 3 else 2

    /** 横滑卡尺寸：紧凑 250×170 / 150×195，大屏 320×210 / 200×250（对齐 iOS LibraryView） */
    fun typeCardWidth(context: Context): Int = if (isRegularWidth(context)) 320 else 250

    fun typeCardHeight(context: Context): Int = if (isRegularWidth(context)) 210 else 170

    fun coverCardWidth(context: Context): Int = if (isRegularWidth(context)) 200 else 150

    fun coverCardHeight(context: Context): Int = if (isRegularWidth(context)) 250 else 195

    /**
     * 班级成员网格列数。
     * 对齐 iOS `GridItem(.adaptive(minimum: 64), spacing: 10)`：
     * 列数 = floor((可用宽度 + 间距) / (最小宽 + 间距))，最少 4 列。
     */
    fun memberGridColumns(context: Context): Int {
        val screenWidth = context.resources.configuration.screenWidthDp
        // 列表左右各 16dp + 班级卡片内边距左右各 16dp
        val available = screenWidth - 64
        return ((available + 10) / 74).coerceAtLeast(4)
    }

    /** 圆形描边（成员头像选中态：自己 2dp 品牌色，他人 1dp 淡色） */
    fun circleStroke(context: Context, color: Int, widthDp: Float): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setStroke(dp(context, widthDp), color)
        }

    /** 给颜色附加透明度（对齐 iOS Color.opacity） */
    fun withAlpha(color: Int, alpha: Float): Int =
        (alpha * 255).toInt().coerceIn(0, 255) shl 24 or (color and 0x00FFFFFF)

    /** 级别标签配色（对齐 iOS Theme.swift 的 levelColor） */
    fun levelColor(context: Context, level: String?): Int? {
        val resId = when (level) {
            "adult" -> R.color.level_adult
            "youth" -> R.color.level_youth
            "kids" -> R.color.level_kids
            "children" -> R.color.level_children
            else -> return null
        }
        return ContextCompat.getColor(context, resId)
    }

    /** 通用胶囊标签（marginEndDp 用于对齐 iOS HStack 的 spacing） */
    fun createChip(
        context: Context,
        text: CharSequence,
        bgColor: Int,
        textColor: Int,
        textSizeSp: Float = 11f,
        bold: Boolean = false,
        marginEndDp: Float = 0f
    ): TextView = TextView(context).apply {
        this.text = text
        textSize = textSizeSp
        setTextColor(textColor)
        gravity = Gravity.CENTER
        background = roundedDrawable(bgColor, dp(context, 999f).toFloat())
        setPadding(dp(context, 8f), dp(context, 3f), dp(context, 8f), dp(context, 3f))
        if (bold) setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            marginEnd = dp(context, marginEndDp)
        }
    }

    /** 级别/分类标签：级别实色白字，普通分类品牌淡底蓝字 */
    fun createCategoryChip(
        context: Context,
        name: String,
        level: String?,
        marginEndDp: Float = 0f
    ): TextView {
        val levelColor = levelColor(context, level)
        return if (levelColor != null) {
            createChip(
                context,
                name,
                levelColor,
                ContextCompat.getColor(context, R.color.white),
                bold = true,
                marginEndDp = marginEndDp
            )
        } else {
            createChip(
                context,
                name,
                ContextCompat.getColor(context, R.color.accent) and 0x00FFFFFF or 0x1F000000,
                ContextCompat.getColor(context, R.color.accent),
                marginEndDp = marginEndDp
            )
        }
    }
}
