package com.yangfei.smrtenglish.base

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.yangfei.smrtenglish.util.EventBusUtil
import com.yangfei.smrtenglish.util.SessionEvent
import com.yangfei.smrtenglish.util.showToast
import org.greenrobot.eventbus.Subscribe
import org.greenrobot.eventbus.ThreadMode
import java.lang.reflect.ParameterizedType

/**
 * Fragment 基类：反射初始化 ViewBinding + ViewModel（对齐参考项目 BaseFragment）
 */
abstract class BaseFragment<VB : ViewBinding, VM : BaseViewModel> : Fragment() {

    private var vb: VB? = null
    val viewBinding: VB get() = vb!!

    /** 页面访问 ViewBinding 的简写 */
    val binding: VB get() = vb!!

    lateinit var viewModel: VM
        private set

    private var isFirstLoad = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (isRegisterEventBus()) {
            EventBusUtil.register(this)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        findView(container)
        return vb!!.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        registerCommonObserver()
        initView(view, savedInstanceState)
        initObserver()
    }

    override fun onResume() {
        super.onResume()
        if (isFirstLoad) {
            loadData()
            isFirstLoad = false
        }
    }

    /** 重新触发首次加载（如登录恢复后刷新） */
    fun refreshFirstLoad() {
        isFirstLoad = true
    }

    abstract fun initView(root: View, savedInstanceState: Bundle?)

    abstract fun initObserver()

    abstract fun loadData()

    override fun onDestroyView() {
        super.onDestroyView()
        vb = null
    }

    override fun onDestroy() {
        if (isRegisterEventBus()) {
            EventBusUtil.unregister(this)
        }
        super.onDestroy()
    }

    /** 是否注册事件分发（会话失效 / 恢复） */
    open fun isRegisterEventBus(): Boolean = true

    @Subscribe(threadMode = ThreadMode.MAIN)
    fun onSessionEventInternal(event: SessionEvent) {
        onSessionEvent(event)
    }

    open fun onSessionEvent(event: SessionEvent) {}

    private fun registerCommonObserver() {
        viewModel.loadingChange.observe(viewLifecycleOwner) {
            val host = activity as? BaseActivity<*, *>
            if (host != null && !host.isFinishing) {
                if (it) host.showLoadingDialog() else host.hideLoadingDialog()
            }
        }
        viewModel.sessionExpired.observe(viewLifecycleOwner) {
            EventBusUtil.post(SessionEvent(SessionEvent.TYPE_INVALID))
        }
        viewModel.dialogErrorTips.observe(viewLifecycleOwner) {
            showToast(it)
        }
    }

    private fun findView(container: ViewGroup?) {
        val type = javaClass.genericSuperclass
        if (type is ParameterizedType) {
            val clazzVB = type.actualTypeArguments[0] as Class<*>
            val method = clazzVB.getMethod(
                "inflate",
                LayoutInflater::class.java,
                ViewGroup::class.java,
                Boolean::class.java
            )
            @Suppress("UNCHECKED_CAST")
            vb = method.invoke(null, layoutInflater, container, false) as VB
            val clazzVM = type.actualTypeArguments[1] as Class<VM>
            viewModel = ViewModelProvider(this)[clazzVM]
        }
    }
}
