package com.yangfei.smrtenglish.ui.cafe

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.CafePage
import com.yangfei.smrtenglish.bean.CafeTab
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

class CafeViewModel : BaseViewModel() {

    val videosResult: MutableLiveData<Response<CafePage>> = MutableLiveData()

    /** 当前 Tab（搜索/分类 Tab 也在这里）；放在 ViewModel 中，屏幕旋转后仍保留 */
    var currentTab: CafeTab = CafeTab.all

    /**
     * @param page 页码，1 为首页
     */
    fun loadVideos(page: Int, showDialog: Boolean = true) {
        load(videosResult, showDialog = showDialog, showErrorToast = false) {
            SmrtApi.cafeVideos(currentTab, page)
        }
    }
}
