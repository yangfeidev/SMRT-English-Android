package com.yangfei.smrtenglish.ui.classes

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi
import com.yangfei.smrtenglish.http.SmrtException

/**
 * 班级列表数据。
 *
 * @param sessionValid 会话是否有效：false → Me 区块显示「登录已过期」+ 重新登录
 *   （对齐 iOS ClassesView 的 sessionValid 语义，不再假装已登录）
 */
data class ClassesData(
    val classes: List<StudentClass> = emptyList(),
    val sessionValid: Boolean = true
)

class ClassesViewModel : BaseViewModel() {

    val classesResult: MutableLiveData<Response<ClassesData>> = MutableLiveData()

    val displayNameResult: MutableLiveData<Response<String>> = MutableLiveData()

    /**
     * 拉班级列表；会话失效时先静默重登（记住过密码时）再重试一次，
     * 仍失败则标记 sessionValid=false（不弹登录页，由 Me 区块给出重新登录入口）。
     */
    fun loadClasses(showDialog: Boolean = true) {
        load(classesResult, showDialog = showDialog, showErrorToast = false) {
            try {
                ClassesData(classes = SmrtApi.myClasses(), sessionValid = true)
            } catch (e: SmrtException) {
                if (!e.isNotLoggedIn()) throw e
                if (SmrtApi.autoReloginIfNeeded()) {
                    ClassesData(classes = SmrtApi.myClasses(), sessionValid = true)
                } else {
                    ClassesData(classes = emptyList(), sessionValid = false)
                }
            }
        }
    }

    /** 当前账号显示名（用于成员列表高亮自己），best-effort */
    fun loadDisplayName() {
        load(displayNameResult, showDialog = false, showErrorToast = false) {
            SmrtApi.refreshMyDisplayName() ?: ""
        }
    }
}
