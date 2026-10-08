package com.yangfei.smrtenglish.http

/**
 * Cookie 的持久化结构体。
 *
 * PHPSESSID 是会话 Cookie（无过期时间），OkHttp 的 JavaNetCookieJar 不会落盘，
 * App 重启即丢，因此手动序列化到本地（与 iOS 把 cookie 写 UserDefaults 同理）。
 */
data class CookieItem(
    var name: String = "",
    var value: String = "",
    var domain: String = "",
    var path: String = "/",
    var expiresAt: Long = Long.MAX_VALUE,
    var hostOnly: Boolean = true,
    var secure: Boolean = false,
    var httpOnly: Boolean = false
)
