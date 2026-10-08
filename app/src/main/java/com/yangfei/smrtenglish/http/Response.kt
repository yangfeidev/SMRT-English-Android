package com.yangfei.smrtenglish.http

/**
 * 统一返回体（对齐参考项目 http/Response）
 *
 * 注意：本项目的"接口"是官网的 SSR 页面，code 不是业务码，
 * 0 表示成功，其余沿用 SmrtException 的错误码。
 */
data class Response<T>(
    var code: Int = CODE_SUCCESS,
    var data: T? = null,
    var message: String? = ""
) {
    companion object {
        const val CODE_SUCCESS = 0
        const val CODE_BAD_RESPONSE = 1001
        const val CODE_LOGIN_FAILED = 1002
        const val CODE_NOT_LOGGED_IN = 1003
        const val CODE_PARSE_FAILED = 1004
    }

    val isSuccessful: Boolean get() = code == CODE_SUCCESS

    /** 登录失效（对齐 iOS SMRTError.notLoggedIn） */
    val isLoginFailure: Boolean get() = code == CODE_NOT_LOGGED_IN
}
