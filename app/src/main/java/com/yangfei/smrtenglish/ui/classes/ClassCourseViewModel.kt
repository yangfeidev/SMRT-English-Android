package com.yangfei.smrtenglish.ui.classes

import androidx.lifecycle.MutableLiveData
import com.yangfei.smrtenglish.base.BaseViewModel
import com.yangfei.smrtenglish.bean.ClassAssignment
import com.yangfei.smrtenglish.bean.ClassCourse
import com.yangfei.smrtenglish.bean.ClassInfoItem
import com.yangfei.smrtenglish.bean.ClassTab
import com.yangfei.smrtenglish.bean.TeacherClassListMember
import com.yangfei.smrtenglish.http.Response
import com.yangfei.smrtenglish.http.SmrtApi
import com.yangfei.smrtenglish.parser.SMRTHTMLParser

class ClassCourseViewModel : BaseViewModel() {

    val courseResult: MutableLiveData<Response<ClassCourse>> = MutableLiveData()

    // 子 Tab 数据（对齐 iOS 子 Tab 视图各自懒加载，首次切到时才拉取）
    val classListResult: MutableLiveData<Response<List<String>>> = MutableLiveData()
    val assignmentsResult: MutableLiveData<Response<List<ClassAssignment>>> = MutableLiveData()
    val classInfoResult: MutableLiveData<Response<List<ClassInfoItem>>> = MutableLiveData()

    /** 教师端 Class List 子页（Current/Removed，对齐 iOS TeacherClassListTabView） */
    val teacherListResult: MutableLiveData<Response<TeacherListPage>> = MutableLiveData()

    /**
     * 教师端 Class List 一次拉取结果：成员 + 所属子页
     * （Current 页顺带记录人数做 badge，对齐 iOS currentCount）
     */
    data class TeacherListPage(
        val isCurrent: Boolean,
        val members: List<TeacherClassListMember>
    )

    /** 各 Tab 是否已拉取过（懒加载一次，切回不再重复请求；重试入口可强制重拉） */
    private val loadedTabs = HashSet<ClassTab>()

    /** 教师端 Class List 子页是否已拉取过（key = "current"/"removed"） */
    private val loadedTeacherPages = HashSet<String>()

    fun isLoaded(tab: ClassTab): Boolean = tab in loadedTabs

    fun isTeacherPageLoaded(isCurrent: Boolean): Boolean =
        (if (isCurrent) "current" else "removed") in loadedTeacherPages

    fun loadCourse(classId: String) {
        load(courseResult, showDialog = false, showErrorToast = false) {
            SmrtApi.classCourse(classId)
        }
        loadedTabs.add(ClassTab.COURSE)
    }

    /**
     * 原生解析子页（学生 Class List / 学生 Assignments / Class Info）。
     * Attendance/Assessments（两角色）与教师端 Assignments/Gradebook 均改
     * WebView 承载（官网自理，结构随官网任意变化免维护），不再走解析。
     */
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
            ClassTab.CLASS_INFO -> load(classInfoResult, showDialog = false, showErrorToast = false) {
                SMRTHTMLParser.parseClassInfo(SmrtApi.classPageHTML(classId, tab))
            }
            else -> {}
        }
    }

    /** 教师端 Class List（Current/Removed 两个 ?tab= 子页同构，解析函数共用） */
    fun loadTeacherClassList(classId: String, isCurrent: Boolean, force: Boolean = false) {
        val key = if (isCurrent) "current" else "removed"
        if (!force && key in loadedTeacherPages) return
        loadedTeacherPages.add(key)
        val tabParam = if (isCurrent) "current-students" else "removed-students"
        load(teacherListResult, showDialog = false, showErrorToast = false) {
            TeacherListPage(
                isCurrent = isCurrent,
                members = SMRTHTMLParser.parseTeacherClassList(
                    SmrtApi.classPageHTML("/sunshine/class/$classId/classlist/?tab=$tabParam")
                )
            )
        }
    }
}
