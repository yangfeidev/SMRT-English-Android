package com.yangfei.smrtenglish.ui.classes

import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.Space
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.bean.ClassMember
import com.yangfei.smrtenglish.databinding.ItemClassMemberBinding
import com.yangfei.smrtenglish.util.UiUtils
import com.yangfei.smrtenglish.util.loadCircle

/**
 * 成员列表渲染（对齐 iOS ClassesView.memberGrid）
 *
 * 每行 4 列起、随可用宽度自适应：圆形头像（首字兜底）+ 姓名；
 * 当前登录账号高亮（加粗 + 品牌色 + 2dp 描边圈 + 「我」徽标）。
 */
object MemberRenderer {

    fun render(
        container: LinearLayout,
        members: List<ClassMember>,
        myDisplayName: String?,
        columns: Int
    ) {
        container.removeAllViews()
        if (members.isEmpty()) return

        val context = container.context
        val inflater = LayoutInflater.from(context)
        val accent = ContextCompat.getColor(context, R.color.accent)
        val perRow = columns.coerceAtLeast(1)

        var row: LinearLayout? = null
        members.forEachIndexed { index, member ->
            if (index % perRow == 0) {
                row = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    clipChildren = false
                    clipToPadding = false
                }
                container.addView(
                    row,
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
            val item = ItemClassMemberBinding.inflate(inflater, row, false)
            val isSelf = !myDisplayName.isNullOrEmpty() && member.name == myDisplayName

            item.tvInitial.text = member.name.take(1)
            item.tvName.text = member.name
            item.tvName.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (isSelf) R.color.accent else R.color.text_secondary
                )
            )
            item.tvName.setTypeface(null, if (isSelf) Typeface.BOLD else Typeface.NORMAL)
            item.tvMeBadge.isVisible = isSelf

            // 描边圈：自己 2dp 品牌色 70%，他人 1dp 品牌色 12%
            item.vRing.background = UiUtils.circleStroke(
                context,
                UiUtils.withAlpha(accent, if (isSelf) 0.7f else 0.12f),
                if (isSelf) 2f else 1f
            )
            item.ivAvatar.loadCircle(member.avatarURL)

            row!!.addView(item.root)
        }

        // 最后一排补空位，保证头像列对齐
        val remain = members.size % perRow
        if (remain != 0) {
            repeat(perRow - remain) {
                row!!.addView(
                    Space(context),
                    LinearLayout.LayoutParams(0, 1, 1f)
                )
            }
        }
    }
}
