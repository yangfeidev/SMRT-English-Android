package com.yangfei.smrtenglish.base

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.blankj.utilcode.util.BarUtils
import com.gyf.immersionbar.ktx.immersionBar
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.util.EventBusUtil
import com.yangfei.smrtenglish.util.SessionEvent
import com.yangfei.smrtenglish.util.showToast
import com.yangfei.smrtenglish.view.TitleBar
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.reflect.ParameterizedType

/**
 * Activity 基类：反射初始化 ViewBinding + ViewModel（对齐参考项目 BaseActivity）
 */
abstract class BaseActivity<VB : ViewBinding, VM : BaseViewModel> : AppCompatActivity() {

    lateinit var viewBinding: VB
        private set

    /** 页面访问 ViewBinding 的简写 */
    val binding: VB get() = viewBinding

    lateinit var viewModel: VM
        private set

    private var loadingDialog: Dialog? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        immersionBar {
            statusBarColor(R.color.transparent)
            navigationBarColor(R.color.white)
            statusBarDarkFont(true)
        }

        if (isRegisterEventBus()) {
            EventBusUtil.register(this)
        }

        beforeContentView()

        // 利用反射，调用指定 ViewBinding 中的 inflate 方法填充视图
        val type = javaClass.genericSuperclass
        if (type is ParameterizedType) {
            val clazzVB = type.actualTypeArguments[0] as Class<*>
            val method = clazzVB.getMethod("inflate", LayoutInflater::class.java)
            @Suppress("UNCHECKED_CAST")
            viewBinding = method.invoke(null, layoutInflater) as VB
            setContentView(viewBinding.root)
            // 如果有状态栏占位，则空出状态栏高度
            initStatusBarHeight(viewBinding.root)
            val clazzVM = type.actualTypeArguments[1] as Class<VM>
            viewModel = ViewModelProvider(this)[clazzVM]
            registerCommonObserver()
            initView(viewBinding.root, savedInstanceState)
            initObserver()
            loadData()
        }
    }

    open fun beforeContentView() {}

    /** 是否注册事件分发（会话失效 / 恢复） */
    protected open fun isRegisterEventBus(): Boolean = true

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSessionEventInternal(event: SessionEvent) {
        onSessionEvent(event)
    }

    open fun onSessionEvent(event: SessionEvent) {}

    override fun onDestroy() {
        if (isRegisterEventBus()) {
            EventBusUtil.unregister(this)
        }
        // 结束前将 dialog 隐藏掉，否则有概率报 leaked window 异常
        loadingDialog?.dismiss()
        loadingDialog = null
        super.onDestroy()
    }

    /** 初始化视图 */
    abstract fun initView(root: View, savedInstanceState: Bundle?)

    /** LiveData 监听 */
    abstract fun initObserver()

    /** 加载数据 */
    abstract fun loadData()

    /** 公共状态监听：loading / 会话失效 / 弹窗错误 */
    private fun registerCommonObserver() {
        viewModel.loadingChange.observe(this) {
            if (it) showLoadingDialog() else hideLoadingDialog()
        }
        viewModel.sessionExpired.observe(this) {
            // 会话失效：交给事件总线，由 MainActivity 统一弹回登录页
            EventBusUtil.post(SessionEvent(SessionEvent.TYPE_INVALID))
        }
        viewModel.dialogErrorTips.observe(this) {
            showToast(it)
        }
    }

    fun showLoadingDialog() {
        if (isFinishing || isDestroyed) return
        if (loadingDialog == null) {
            loadingDialog = Dialog(this, R.style.LoadingDialogStyle).apply {
                setContentView(R.layout.dialog_loading)
                setCanceledOnTouchOutside(false)
                setCancelable(true)
            }
        }
        if (loadingDialog?.isShowing == false) loadingDialog?.show()
    }

    fun hideLoadingDialog() {
        if (!isFinishing && loadingDialog?.isShowing == true) {
            loadingDialog?.dismiss()
        }
    }

    /** 统一获取 TitleBar（布局里 id 固定为 top_bar_view） */
    fun requireTitleBar(callback: (TitleBar) -> Unit) {
        findViewById<TitleBar>(R.id.top_bar_view)?.let { callback.invoke(it) }
    }

    private fun initStatusBarHeight(root: View) {
        root.findViewById<View>(R.id.common_top_bar_view)?.let {
            it.layoutParams.height = BarUtils.getStatusBarHeight()
        }
    }

    fun finishWithAnim() {
        finish()
        overridePendingTransition(0, 0)
    }
}
