package com.yangfei.smrtenglish.base

import android.app.Application
import com.blankj.utilcode.util.LogUtils
import com.blankj.utilcode.util.Utils
import com.hjq.toast.ToastUtils
import com.tencent.mmkv.MMKV
import com.yangfei.smrtenglish.BuildConfig
import com.yangfei.smrtenglish.constant.UrlConstant

class BaseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        _context = this
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

    companion object {
        private var _context: BaseApplication? = null

        @JvmStatic
        fun globalContext(): BaseApplication = _context!!
    }
}
