package com.yangfei.smrtenglish.ui.library

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.CourseUnit
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi

class CourseDetailViewModel : BaseViewModel() {

    val unitsResult: MutableLiveData<Response<List<CourseUnit>>> = MutableLiveData()

    fun loadUnits(courseId: String) {
        load(unitsResult, showDialog = false, showErrorToast = false) {
            SmrtApi.courseUnits(courseId)
        }
    }
}
