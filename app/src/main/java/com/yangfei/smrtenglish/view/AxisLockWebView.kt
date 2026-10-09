package com.yangfei.smrtenglish.view

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.webkit.WebView
import kotlin.math.abs

/**
 * 宽表格页滚动轴锁 WebView（对齐 iOS AxisLockScrollDelegate）：
 * 拖动开始时判定主导轴（水平/垂直），本次拖动只允许该轴滚动——避免斜向
 * 拖动时 x/y 同时滚产生"360 度转圈"的失控感。
 *
 * iOS 用转发 scrollView.delegate 拦截拖动回调；Android WebView 的滚动
 * 最终经 scrollTo 落地，拖动期间把非主导轴的位移钉在拖动起始值即可。
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
    }

    private var axisLockEnabled = false

    /** 锁定阈值（px，按屏幕密度换算 6dp） */
    private val lockThresholdPx = AXIS_LOCK_THRESHOLD_DP * resources.displayMetrics.density

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var lockedAxis = AXIS_NONE

    /** 主导轴锁定时的起始滚动位置（对齐 iOS lockedOffset：钉住起始值含边界回弹） */
    private var lockedScrollX = 0
    private var lockedScrollY = 0

    fun setAxisLockEnabled(enabled: Boolean) {
        axisLockEnabled = enabled
        if (!enabled) lockedAxis = AXIS_NONE
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (axisLockEnabled) {
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
                        if (dx > lockThresholdPx && dx >= dy) {
                            lockedAxis = AXIS_HORIZONTAL
                            lockedScrollX = scrollX
                            lockedScrollY = scrollY
                        } else if (dy > lockThresholdPx) {
                            lockedAxis = AXIS_VERTICAL
                            lockedScrollX = scrollX
                            lockedScrollY = scrollY
                        }
                    }
                }
                // 多指（捏合缩放）：不锁轴，避免轴锁与缩放互相拉扯产生抖动
                MotionEvent.ACTION_POINTER_DOWN -> lockedAxis = AXIS_NONE
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> lockedAxis = AXIS_NONE
            }
        }
        return super.onTouchEvent(event)
    }

    override fun scrollTo(x: Int, y: Int) {
        if (!axisLockEnabled) {
            super.scrollTo(x, y)
            return
        }
        when (lockedAxis) {
            // 水平主导：钉住纵向（只沿 x 滚）
            AXIS_HORIZONTAL -> super.scrollTo(x, lockedScrollY)
            // 垂直主导：钉住横向
            AXIS_VERTICAL -> super.scrollTo(lockedScrollX, y)
            else -> super.scrollTo(x, y)
        }
    }
}
