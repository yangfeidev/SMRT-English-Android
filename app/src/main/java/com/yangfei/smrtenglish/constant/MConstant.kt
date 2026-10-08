package com.yangfei.smrtenglish.constant

/**
 * 存储 key 常量
 */
object MConstant {

    /** 登录邮箱（对齐 iOS smrt.account.email） */
    const val ACCOUNT_EMAIL = "smrt.account.email"

    /** 记住的密码（对齐 iOS Keychain） */
    const val ACCOUNT_PASSWORD = "smrt.account.password"

    /** 账号显示名（中式「姓+名」） */
    const val ACCOUNT_DISPLAY_NAME = "smrt.account.displayName"

    /** 用户主动退出登录的标记：保留账密回填，但禁止静默自动重登 */
    const val LOGGED_OUT_BY_USER = "smrt.logged.out.by.user"

    /** 手动持久化的会话 Cookie（PHPSESSID 无过期时间，OkHttp 不会落盘） */
    const val PERSISTED_COOKIES = "smrt.persisted.cookies"

    /** Cafe 分类/搜索 Tab 缓存 */
    const val CAFE_TAB_PATH = "smrt.cafe.tab.path"
}
