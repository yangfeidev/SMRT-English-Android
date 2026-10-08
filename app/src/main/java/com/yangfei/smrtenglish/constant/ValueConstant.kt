package com.yangfei.smrtenglish.constant

/**
 * 通用取值常量
 */
object ValueConstant {

    /** 列表分页大小（Cafe 分页由官网 ?page=N 控制） */
    const val CAFE_FIRST_PAGE = 1

    /** Classes Tab 会话校验节流：5 分钟内不重复校验（对齐 iOS） */
    const val SESSION_CHECK_INTERVAL = 5 * 60 * 1000L

    /** 网络超时时间（秒） */
    const val TIMEOUT_SECONDS = 30L
}
