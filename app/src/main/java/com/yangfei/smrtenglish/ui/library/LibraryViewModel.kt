package com.yangfei.smrtenglish.ui.library

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.LibraryHome
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

class LibraryViewModel : BaseViewModel() {

    val homeResult: MutableLiveData<Response<LibraryHome>> = MutableLiveData()

    fun loadHome(showDialog: Boolean = false) {
        load(homeResult, showDialog = showDialog, showErrorToast = false) {
            SmrtApi.libraryHome()
        }
    }
}
