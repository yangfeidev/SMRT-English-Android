package com.yangfei.smrtenglish.base

import android.app.Activity
import android.app.Application
import android.os.Bundle
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.ToastUtils
import com.tencent.mmkv.MMKV
import com.yangfei.smrtenglish.BuildConfig
import com.yangfei.smrtenglish.constant.UrlConstant

class BaseApplication : Application(), Application.ActivityLifecycleCallbacks {

    /** 已 start 的 Activity 计数：>0 = App 在前台（对齐 iOS willEnterForeground 判定） */
    private var startedActivityCount = 0

    override fun onCreate() {
        super.onCreate()
        _context = this
        registerActivityLifecycleCallbacks(this)
        // 初始化 AndroidUtilCode
        Utils.init(this)
        // 初始化 MMKV
        MMKV.initialize(this)
        // 初始化 Toast
        ToastUtils.init(this)
        // 日志开关
        LogUtils.getConfig().setLogSwitch(BuildConfig.DEBUG)
            .setLog2FileSwitch(false)
        LogUtils.i(UrlConstant.HTTP_LOG, "App started, base url = ${UrlConstant.BASE_URL}")
    }

    /** App 是否在前台（至少一个 Activity 处于 started 状态） */
    fun isAppInForeground(): Boolean = startedActivityCount > 0

    // MARK: - ActivityLifecycleCallbacks（仅计数，其余空实现）

    override fun onActivityStarted(activity: Activity) {
        startedActivityCount++
    }

    override fun onActivityStopped(activity: Activity) {
        startedActivityCount--
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
    override fun onActivityResumed(activity: Activity) {}
    override fun onActivityPaused(activity: Activity) {}
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
    override fun onActivityDestroyed(activity: Activity) {}

    companion object {
        private var _context: BaseApplication? = null

        @JvmStatic
        fun globalContext(): BaseApplication = _context!!
    }
}
