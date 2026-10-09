package com.yangfei.smrtenglish.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.abs

/**
 * 宽表格页滚动轴锁 WebView（对齐 iOS AxisLockScrollDelegate）：
 * 拖动开始时判定主导轴（水平/垂直），本次拖动只允许该轴滚动——避免斜向
 * 拖动时 x/y 同时滚产生「360 度转圈」的失控感。
 *
 * ## 为什么在 onTouchEvent 层锁，而不是覆写 scrollTo
 *
 * 早期实现覆写 `View.scrollTo()` 钉住非主导轴，真机实测**完全无效**：
 * 斜向拖动时 onTouch 收到 72 次事件、轴锁也正确判定为水平，但 scrollTo 调用 0 次。
 * 原因是宽视口页（meta viewport width=992 + initial-scale）下 WebView 的平移
 * 不走 `View.scrollTo`，而走其内部合成器/手势系统（与捏合缩放同一套）。
 *
 * 因此改为**改写 MotionEvent**：锁定轴后，把非主导轴的坐标钉在锁定时刻的值，
 * 后续 MOVE 事件在该轴上位移恒为 0，WebView 内部无论走哪条路径都收不到另一轴的位移。
 *
 * ## 缩放与多指
 *
 * 捏合（ACTION_POINTER_DOWN）不锁轴，避免轴锁与缩放互相拉扯；抬指后重置。
 * 仅教师端宽表格页（fixedViewportWidth != null）启用，其余页面零影响。
 *
 * 注意：该页必须同时注入 WIDE_VIEWPORT_PAN_CSS（html{overflow-x:auto}），
 * 否则横向滚动范围为 0，轴锁与横向平移都无从发生。
 */
class AxisLockWebView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : WebView(context, attrs, defStyleAttr) {

    companion object {
        private const val AXIS_NONE = 0
        private const val AXIS_HORIZONTAL = 1
        private const val AXIS_VERTICAL = 2

        /** 拖动超过该距离后锁定主导轴（对齐 iOS 的 6pt，按密度换算成 px） */
        private const val AXIS_LOCK_THRESHOLD_DP = 6f

        /**
         * 锁定后的位移死区（px）：非主导轴允许 1px 以内抖动，避免手指自然
         * 抖动被完全钉死导致手感发涩；超过则完全归零。
         */
        private const val AXIS_DEAD_ZONE_PX = 1f
    }

    private var axisLockEnabled = false

    /** 锁定阈值（px，按屏幕密度换算 6dp） */
    private val lockThresholdPx = AXIS_LOCK_THRESHOLD_DP * resources.displayMetrics.density

    /** 本次拖动的起点（原始坐标，用于判定主导轴，不受改写影响） */
    private var touchStartX = 0f
    private var touchStartY = 0f

    private var lockedAxis = AXIS_NONE

    /** 锁定时刻的坐标：非主导轴后续 MOVE 一律钉在此处 */
    private var lockedX = 0f
    private var lockedY = 0f

    fun setAxisLockEnabled(enabled: Boolean) {
        axisLockEnabled = enabled
        if (!enabled) lockedAxis = AXIS_NONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!axisLockEnabled) return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                lockedAxis = AXIS_NONE
            }

            MotionEvent.ACTION_MOVE -> {
                if (lockedAxis == AXIS_NONE) {
                    val dx = abs(event.x - touchStartX)
                    val dy = abs(event.y - touchStartY)
                    // 判定用原始坐标；达标后记下当前坐标作为该轴的钉住基准
                    if (dx > lockThresholdPx && dx >= dy) {
                        lockedAxis = AXIS_HORIZONTAL
                        lockedX = event.x
                        lockedY = event.y
                    } else if (dy > lockThresholdPx) {
                        lockedAxis = AXIS_VERTICAL
                        lockedX = event.x
                        lockedY = event.y
                    }
                }
                if (lockedAxis != AXIS_NONE) {
                    val rewritten = rewrite(event)
                    val handled = super.onTouchEvent(rewritten)
                    // obtain 出来的副本必须回收，否则 MotionEvent 池会被拖空
                    if (rewritten !== event) rewritten.recycle()
                    return handled
                }
            }

            // 多指（捏合缩放）：不锁轴，避免轴锁与缩放互相拉扯产生抖动
            MotionEvent.ACTION_POINTER_DOWN -> lockedAxis = AXIS_NONE
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lockedAxis = AXIS_NONE
        }
        return super.onTouchEvent(event)
    }

    /**
     * 把非主导轴的坐标钉在锁定基准上，返回改写后的副本（原事件由调用方回收）。
     * 主导轴原样透传，保证该方向滚动/惯性完全不受影响。
     */
    private fun rewrite(event: MotionEvent): MotionEvent {
        val keepX = if (lockedAxis == AXIS_HORIZONTAL) event.x else lockedX
        val keepY = if (lockedAxis == AXIS_VERTICAL) event.y else lockedY
        if (abs(keepX - event.x) <= AXIS_DEAD_ZONE_PX &&
            abs(keepY - event.y) <= AXIS_DEAD_ZONE_PX
        ) {
            return event
        }
        val copy = MotionEvent.obtain(event)
        // 用 offset 平移而非 setLocation：多指事件下各 pointer 一起搬移，
        // 保持相对关系不变（单指时等价于 setLocation）
        copy.offsetLocation(keepX - event.x, keepY - event.y)
        return copy
    }
}
