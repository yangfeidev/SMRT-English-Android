package com.yangfei.smrtenglish.ui.classes

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.ClassAssignment
import com.yangfei.smrtenglish.bean.ClassCourse
import com.yangfei.smrtenglish.bean.ClassInfoItem
import com.yangfei.smrtenglish.bean.ClassTab
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi
import com.yangfei.smrtenglish.parser.SMRTHTMLParser

class ClassCourseViewModel : BaseViewModel() {

    val courseResult: MutableLiveData<Response<ClassCourse>> = MutableLiveData()

    // 子 Tab 数据（对齐 iOS 子 Tab 视图各自懒加载，首次切到时才拉取）
    val classListResult: MutableLiveData<Response<List<String>>> = MutableLiveData()
    val assignmentsResult: MutableLiveData<Response<List<ClassAssignment>>> = MutableLiveData()
    /** true = 官网为空态（No Attendance Records） */
    val attendanceResult: MutableLiveData<Response<Boolean>> = MutableLiveData()
    /** true = 官网为空态（No Assessment Results） */
    val assessmentsResult: MutableLiveData<Response<Boolean>> = MutableLiveData()
    val classInfoResult: MutableLiveData<Response<List<ClassInfoItem>>> = MutableLiveData()

    /** 各 Tab 是否已拉取过（懒加载一次，切回不再重复请求；重试入口可强制重拉） */
    private val loadedTabs = HashSet<ClassTab>()

    fun isLoaded(tab: ClassTab): Boolean = tab in loadedTabs

    fun loadCourse(classId: String) {
        load(courseResult, showDialog = false, showErrorToast = false) {
            SmrtApi.classCourse(classId)
        }
        loadedTabs.add(ClassTab.COURSE)
    }

    fun loadTabPage(classId: String, tab: ClassTab, force: Boolean = false) {
        if (!force && tab in loadedTabs) return
        loadedTabs.add(tab)
        when (tab) {
            ClassTab.COURSE -> loadCourse(classId)
            ClassTab.CLASS_LIST -> load(classListResult, showDialog = false, showErrorToast = false) {
                SMRTHTMLParser.parseClassListMembers(SmrtApi.classPageHTML(classId, tab))
            }
            ClassTab.ASSIGNMENTS -> load(assignmentsResult, showDialog = false, showErrorToast = false) {
                SMRTHTMLParser.parseClassAssignments(SmrtApi.classPageHTML(classId, tab))
            }
            ClassTab.ATTENDANCE -> load(attendanceResult, showDialog = false, showErrorToast = false) {
                !SMRTHTMLParser.parseClassAttendanceEmpty(SmrtApi.classPageHTML(classId, tab))
            }
            ClassTab.ASSESSMENTS -> load(assessmentsResult, showDialog = false, showErrorToast = false) {
                !SMRTHTMLParser.parseClassAssessmentsEmpty(SmrtApi.classPageHTML(classId, tab))
            }
            ClassTab.CLASS_INFO -> load(classInfoResult, showDialog = false, showErrorToast = false) {
                SMRTHTMLParser.parseClassInfo(SmrtApi.classPageHTML(classId, tab))
            }
        }
    }
}
