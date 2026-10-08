package com.yangfei.smrtenglish.ui.main

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

class MainViewModel : BaseViewModel() {

    /** 会话校验结果：true=有效 */
    val sessionCheckResult: MutableLiveData<Response<Boolean>> = MutableLiveData()

    /** 切到 Classes Tab 时校验会话，失效先静默重登一次（对齐 iOS MainTabView） */
    fun validateSession() {
        load(sessionCheckResult, showDialog = false, showErrorToast = false) {
            var valid = SmrtApi.checkSession()
            if (!valid) valid = SmrtApi.autoReloginIfNeeded()
            valid
        }
    }
}
