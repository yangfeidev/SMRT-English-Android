package com.yangfei.smrtenglish.util

import org.greenrobot.eventbus.EventBus

/**
 * 事件总线统一入口（对齐 iOS NotificationCenter 的会话通知）
 */
object EventBusUtil {

    fun register(subscriber: Any) {
        if (!EventBus.getDefault().isRegistered(subscriber)) {
            EventBus.getDefault().register(subscriber)
        }
    }

    fun unregister(subscriber: Any) {
        if (EventBus.getDefault().isRegistered(subscriber)) {
            EventBus.getDefault().unregister(subscriber)
        }
    }

    fun post(event: Any) = EventBus.getDefault().post(event)
}

/**
 * 会话事件：失效 / 恢复
 */
data class SessionEvent(val type: Int) {
    companion object {
        /** 会话失效（退出登录 / 被踢） */
        const val TYPE_INVALID = 1

        /** 登录成功，需要登录态的页面刷新数据 */
        const val TYPE_RESTORED = 2
    }
}
