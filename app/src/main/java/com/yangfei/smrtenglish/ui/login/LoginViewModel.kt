package com.yangfei.smrtenglish.ui.login

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.base.manager.SessionManager
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

/**
 * 登录 / 忘记密码（对齐 iOS LoginView + ForgotPasswordView）
 */
class LoginViewModel : BaseViewModel() {

    val loginResult: MutableLiveData<Response<Boolean>> = MutableLiveData()

    val forgotResult: MutableLiveData<Response<Boolean>> = MutableLiveData()

    /** 账密总是记住（对齐 iOS Keychain 默认行为） */
    fun login(email: String, password: String) {
        load(loginResult, showDialog = true, showErrorToast = false) {
            val ok = SmrtApi.login(email, password)
            if (ok) SessionManager.saveCredentials(email, password)
            ok
        }
    }

    fun forgotPassword(email: String) {
        load(forgotResult, showDialog = true, showErrorToast = false) {
            SmrtApi.forgotPassword(email)
            true
        }
    }
}
