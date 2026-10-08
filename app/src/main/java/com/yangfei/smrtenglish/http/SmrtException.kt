package com.yangfei.smrtenglish.http

/**
 * 业务异常（对齐 iOS SMRTError）
 */
class SmrtException(val errorCode: Int, message: String?) : RuntimeException(message) {

    companion object {
        fun badResponse() = SmrtException(Response.CODE_BAD_RESPONSE, "网络响应异常")
        fun loginFailed() = SmrtException(Response.CODE_LOGIN_FAILED, "邮箱或密码错误")
        fun notLoggedIn() = SmrtException(Response.CODE_NOT_LOGGED_IN, "登录已过期，请重新登录")
        fun parseFailed() = SmrtException(Response.CODE_PARSE_FAILED, "页面解析失败，网站结构可能已变更")
    }

    fun isNotLoggedIn(): Boolean = errorCode == Response.CODE_NOT_LOGGED_IN
}
