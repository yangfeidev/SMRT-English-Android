package com.yangfei.smrtenglish.base.manager

import com.yangfei.smrtenglish.constant.MConstant
import com.yangfei.smrtenglish.http.Request
import com.yangfei.smrtenglish.util.AccountRoleEvent
import com.yangfei.smrtenglish.util.EventBusUtil
import com.yangfei.smrtenglish.util.MMKVUtils
import com.yangfei.smrtenglish.util.SessionEvent

/**
 * 登录态管理（对齐 iOS Keychain + UserDefaults 中保存的账号信息）
 */
object SessionManager {

    /** 上次登录邮箱 */
    var email: String?
        get() = MMKVUtils.getString(MConstant.ACCOUNT_EMAIL, "").ifEmpty { null }
        set(value) = MMKVUtils.putString(MConstant.ACCOUNT_EMAIL, value)

    /** 记住的密码（对齐 iOS Keychain，账密总是记住） */
    var password: String?
        get() = MMKVUtils.getString(MConstant.ACCOUNT_PASSWORD, "").ifEmpty { null }
        set(value) = MMKVUtils.putString(MConstant.ACCOUNT_PASSWORD, value)

    /** 账号显示名（中式「姓+名」） */
    var displayName: String?
        get() = MMKVUtils.getString(MConstant.ACCOUNT_DISPLAY_NAME, "").ifEmpty { null }
        set(value) = MMKVUtils.putString(MConstant.ACCOUNT_DISPLAY_NAME, value)

    /** 用户主动退出登录的标记。 */
    var loggedOutByUser: Boolean
        get() = MMKVUtils.getBoolean(MConstant.LOGGED_OUT_BY_USER, false)
        set(value) = MMKVUtils.putBoolean(MConstant.LOGGED_OUT_BY_USER, value)

    /** 账号角色持久化缓存（教师号 true；未知默认 false 学生，对齐 iOS smrt.account.isTeacher） */
    var isTeacher: Boolean
        get() = MMKVUtils.getBoolean(MConstant.ACCOUNT_IS_TEACHER, false)
        private set(value) = MMKVUtils.putBoolean(MConstant.ACCOUNT_IS_TEACHER, value)

    /**
     * 更新账号角色并广播（值不变时不发，对齐 iOS SMRTClient.updateAccountRole）。
     * - classes 列表页「Join a Class」入口判定（学生号特征）兜底
     * - 班级 course 页侧栏 gradebook 链接做硬校正
     * Me 区块角色徽标监听 AccountRoleEvent 刷新。
     */
    fun updateAccountRole(teacher: Boolean) {
        if (isTeacher == teacher) return
        isTeacher = teacher
        EventBusUtil.post(AccountRoleEvent(teacher))
    }

    fun saveCredentials(accountEmail: String, accountPassword: String) {
        email = accountEmail
        password = accountPassword
        loggedOutByUser = false
    }

    /**
     * 退出登录：清 cookie 与显示名，但**保留账密**用于下次登录页回填。
     *
     * 说明：iOS 的 Keychain 以 `account = 邮箱` 为索引，登出清掉邮箱后密码就取不到了，
     * 因此 iOS 退出后登录页两个输入框都是空的；这里按产品需要保留回填，
     * 并用 loggedOutByUser 标记保证不会因此被静默重登。
     */
    fun logout() {
        Request.cookieJar.clear()
        displayName = null
        // 角色缓存一并清理（对齐 iOS logout 清 roleKey）；下次登录由
        // classes/course 页解析重新判定
        isTeacher = false
        loggedOutByUser = true
        EventBusUtil.post(SessionEvent(SessionEvent.TYPE_INVALID))
    }

    /** 是否已有可自动回填的账密 */
    fun hasSavedCredentials(): Boolean = !email.isNullOrEmpty() && !password.isNullOrEmpty()
}
