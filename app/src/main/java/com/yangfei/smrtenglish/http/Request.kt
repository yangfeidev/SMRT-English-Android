package com.yangfei.smrtenglish.http

import com.blankj.utilcode.util.LogUtils
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.constant.ValueConstant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/**
 * 网络请求统一入口（对齐参考项目 http/Request）
 *
 * 与参考项目不同：官网没有 JSON API，全部是服务端渲染 HTML，
 * 因此这里不解析 code/data，只负责抓页面并把 HTML / 最终 URL 交回上层。
 */
object Request {

    /** 登录态客户端（带持久化 Cookie） */
    val cookieJar = PersistentCookieJar()

    val client: OkHttpClient by lazy { buildClient(cookieJar) }

    /** 游客客户端（无 Cookie，用于 Cafe 公开内容，对齐 iOS guestSession） */
    val guestClient: OkHttpClient by lazy { buildClient(null) }

    private fun buildClient(jar: PersistentCookieJar?): OkHttpClient {
        val builder = OkHttpClient.Builder()
            .connectTimeout(ValueConstant.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(ValueConstant.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(ValueConstant.TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
        if (jar != null) {
            builder.cookieJar(jar)
        }
        return builder.build()
    }

    /** 一次性请求结果：页面 HTML + 重定向后的最终地址 */
    data class HtmlResult(val html: String, val finalUrl: String, val code: Int)

    /**
     * GET 页面。
     *
     * @param path 页面路径（可含 query，如 /sunshine/cafe?page=2）
     * @param asGuest 是否走游客会话（无登录 Cookie）
     * @param requirePathContains 校验重定向后最终 URL 路径必须包含此片段，否则视为会话失效。
     *   官网在会话过期/被顶掉时会 302 到营销首页 /smrt/，只看内容会误判为"登录有效但数据为空"。
     */
    suspend fun getHtml(
        path: String,
        asGuest: Boolean = false,
        requirePathContains: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            val url = UrlConstant.fullUrl(path)
            LogUtils.i(UrlConstant.HTTP_LOG, "GET[${if (asGuest) "guest" else "auth"}] $url")
            val request = Request.Builder().url(url).get().build()
            val httpClient = if (asGuest) guestClient else client
            val response = httpClient.newCall(request).execute()
            response.use {
                if (!it.isSuccessful) {
                    LogUtils.e(UrlConstant.HTTP_LOG, "GET $url failed: ${it.code}")
                    throw SmrtException.badResponse()
                }
                val finalUrl = it.request.url.toString()
                val html = it.body?.string() ?: throw SmrtException.badResponse()
                if (requirePathContains != null && !finalUrl.contains(requirePathContains)) {
                    LogUtils.w(UrlConstant.HTTP_LOG, "redirected to $finalUrl, session invalid")
                    throw SmrtException.notLoggedIn()
                }
                html
            }
        }
    }

    /**
     * POST 表单（登录 / 忘记密码）。
     * OkHttp 默认跟随 302，登录成功后最终 URL 会落到 /sunshine/cafe。
     */
    suspend fun postForm(
        path: String,
        params: Map<String, String>,
        asGuest: Boolean = false
    ): HtmlResult {
        return withContext(Dispatchers.IO) {
            val url = UrlConstant.fullUrl(path)
            val builder = FormBody.Builder()
            params.forEach { (k, v) -> builder.add(k, v) }
            val request = Request.Builder().url(url).post(builder.build()).build()
            val httpClient = if (asGuest) guestClient else client
            val response = httpClient.newCall(request).execute()
            response.use {
                val html = it.body?.string().orEmpty()
                HtmlResult(html, it.request.url.toString(), it.code)
            }
        }
    }

    /** Range 请求字节区间，返回 null 表示失败（用于视频编码探测） */
    suspend fun fetchBytes(url: String, start: Long, end: Long): ByteArray? {
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(url)
                    .header("Range", "bytes=$start-$end")
                    .build()
                client.newCall(request).execute().use { response ->
                    if (response.code != 206 && response.code != 200) return@withContext null
                    response.body?.bytes()
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
