package com.yangfei.smrtenglish.ui.library

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

class CourseListViewModel : BaseViewModel() {

    val coursesResult: MutableLiveData<Response<List<LibraryCourse>>> = MutableLiveData()

    fun loadCourses(type: String, showDialog: Boolean = false) {
        load(coursesResult, showDialog = showDialog, showErrorToast = false) {
            SmrtApi.courses(type)
        }
    }
}
