package com.yangfei.smrtenglish.http

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.yangfei.smrtenglish.constant.MConstant
import com.yangfei.smrtenglish.util.MMKVUtils
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * 可持久化的 CookieJar（对齐 iOS 手动持久化 HTTPCookieStorage 的做法）
 *
 * 登录态依赖 PHPSESSID + cookname + cookid 三个会话 Cookie，必须自己落盘，
 * 否则 App 重启后会话丢失。
 */
class PersistentCookieJar : CookieJar {

    private val gson = Gson()

    private val cache = ArrayList<Cookie>()

    init {
        restore()
    }

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        synchronized(this) {
            for (cookie in cookies) {
                removeSame(cookie)
                cache.add(cookie)
            }
        }
        persist()
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val result = ArrayList<Cookie>()
        synchronized(this) {
            val iterator = cache.iterator()
            while (iterator.hasNext()) {
                val cookie = iterator.next()
                if (cookie.expiresAt < now) {
                    iterator.remove()
                    continue
                }
                if (cookie.matches(url)) {
                    result.add(cookie)
                }
            }
        }
        if (result.isEmpty()) return emptyList()
        // Cookie 头只带 name=value，避免重复携带属性
        return result
    }

    /** 当前全部 Cookie（供 WebView 同步登录态使用） */
    fun allCookies(): List<Cookie> {
        synchronized(this) { return ArrayList(cache) }
    }

    fun clear() {
        synchronized(this) { cache.clear() }
        MMKVUtils.remove(MConstant.PERSISTED_COOKIES)
    }

    private fun removeSame(cookie: Cookie) {
        val iterator = cache.iterator()
        while (iterator.hasNext()) {
            val old = iterator.next()
            if (old.name == cookie.name && old.domain == cookie.domain && old.path == cookie.path) {
                iterator.remove()
            }
        }
    }

    private fun persist() {
        val items = synchronized(this) {
            cache.map {
                CookieItem(
                    name = it.name,
                    value = it.value,
                    domain = it.domain,
                    path = it.path,
                    expiresAt = it.expiresAt,
                    hostOnly = it.hostOnly,
                    secure = it.secure,
                    httpOnly = it.httpOnly
                )
            }
        }
        MMKVUtils.putString(MConstant.PERSISTED_COOKIES, gson.toJson(items))
    }

    private fun restore() {
        val json = MMKVUtils.getString(MConstant.PERSISTED_COOKIES, "")
        if (json.isEmpty()) return
        val type = object : TypeToken<List<CookieItem>>() {}.type
        val items = try {
            gson.fromJson<List<CookieItem>>(json, type)
        } catch (e: Exception) {
            null
        } ?: return
        val now = System.currentTimeMillis()
        for (item in items) {
            if (item.expiresAt < now) continue
            val builder = Cookie.Builder()
                .name(item.name)
                .value(item.value)
                .expiresAt(item.expiresAt)
            if (item.hostOnly) {
                builder.hostOnlyDomain(item.domain)
            } else {
                builder.domain(item.domain)
            }
            builder.path(item.path)
            if (item.secure) builder.secure()
            if (item.httpOnly) builder.httpOnly()
            cache.add(builder.build())
        }
    }
}
