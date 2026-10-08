package com.yangfei.smrtenglish.base

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtException
import com.yangfei.smrtenglish.util.showToast
import com.yangfei.smrtenglish.util.unpeeklivedata.UnPeekLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ViewModel 基类（对齐参考项目 BaseViewModel 的 loading / 错误 / 登录失效 统一处理）
 *
 * 页面统一走 ViewModel -> viewModelScope.launch -> load() -> Request 的链路。
 */
open class BaseViewModel : ViewModel() {

    /** 是否显示加载弹窗 */
    val loadingChange: UnPeekLiveData<Boolean> = UnPeekLiveData()

    /** 会话失效（对齐 iOS smrt.session.invalid） */
    val sessionExpired: UnPeekLiveData<Boolean> = UnPeekLiveData()

    /** 弹窗级错误提示 */
    val dialogErrorTips: UnPeekLiveData<String> = UnPeekLiveData()

    /**
     * 发起一次页面/数据请求。
     *
     * @param liveData 结果回传（成功 code=0；失败 code 为 SmrtException 的错误码）
     * @param showDialog 是否显示加载弹窗
     * @param showErrorToast 失败是否 Toast 提示
     * @param block 真正的抓取/解析逻辑（运行在 IO 线程）
     */
    protected fun <T> load(
        liveData: MutableLiveData<Response<T>>,
        showDialog: Boolean = true,
        showErrorToast: Boolean = true,
        block: suspend () -> T
    ) {
        viewModelScope.launch {
            if (showDialog) loadingChange.value = true
            try {
                val data = withContext(Dispatchers.IO) { block() }
                liveData.value = Response(data = data)
            } catch (e: Exception) {
                onRequestFailed(e, liveData, showErrorToast)
            } finally {
                if (showDialog) loadingChange.value = false
            }
        }
    }

    /**
     * 不回传数据的一次性请求（登录、忘记密码等）
     */
    protected fun submit(
        showDialog: Boolean = true,
        showErrorToast: Boolean = true,
        onSuccess: () -> Unit,
        block: suspend () -> Unit
    ) {
        viewModelScope.launch {
            if (showDialog) loadingChange.value = true
            try {
                withContext(Dispatchers.IO) { block() }
                onSuccess()
            } catch (e: Exception) {
                onRequestFailed<Any>(e, null, showErrorToast)
            } finally {
                if (showDialog) loadingChange.value = false
            }
        }
    }

    private fun <T> onRequestFailed(
        e: Exception,
        liveData: MutableLiveData<Response<T>>?,
        showErrorToast: Boolean
    ) {
        if (e is SmrtException) {
            if (e.isNotLoggedIn()) {
                // 会话失效：交给 BaseActivity 统一弹登录页
                sessionExpired.value = true
                return
            }
            liveData?.value = Response(code = e.errorCode, message = e.message)
            if (showErrorToast) showToast(e.message)
        } else {
            liveData?.value = Response(
                code = Response.CODE_BAD_RESPONSE,
                message = e.message ?: "网络异常"
            )
            if (showErrorToast) showToast("网络异常，请稍后重试")
        }
    }
}
