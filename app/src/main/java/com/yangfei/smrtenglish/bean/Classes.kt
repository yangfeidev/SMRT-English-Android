package com.yangfei.smrtenglish.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** 班级成员（名字来自头像的 tooltip） */
@Parcelize
data class ClassMember(
    val id: String = "",
    val name: String = "",
    /** 成员头像 URL（多数为站点默认头像 default1.gif） */
    val avatarURL: String? = null
) : Parcelable

/** 我的班级（/sunshine/classes 列表项） */
@Parcelize
data class StudentClass(
    /** 班级数字 id（如 25057） */
    val id: String = "",
    val name: String = "",
    val course: String = "",
    /** 班级课程页路径 */
    val coursePath: String = "",
    val members: List<ClassMember> = emptyList()
) : Parcelable

/** 班级课程页课时行 */
@Parcelize
data class ClassLesson(
    val id: String = "",
    val courseId: String = "",
    val number: String = "",
    val title: String = "",
    /** 官网 lesson-status 的 tippy 文案：In Progress / Lesson only / Not shared */
    val status: String = "",
    val hasExercises: Boolean = false
) : Parcelable

/** 班级课程页分组（category-header） */
@Parcelize
data class ClassSection(
    val title: String = "",
    val lessons: List<ClassLesson> = emptyList()
) : Parcelable {
    val id: String get() = title
}

/** 班级课程页单元（unit-section，手风琴折叠） */
@Parcelize
data class ClassUnit(
    val id: String = "",
    val name: String = "",
    val number: String = "",
    val imageURL: String? = null,
    val sections: List<ClassSection> = emptyList()
) : Parcelable

/** 班级课程页整页数据 */
@Parcelize
data class ClassCourse(
    val className: String = "",
    val courseName: String = "",
    /** 课程在 Library 的 id（页面 folder_open 按钮的 /sunshine/library/{id}） */
    val courseLibraryId: String = "",
    val units: List<ClassUnit> = emptyList(),
    /** 是否教师端（班级页侧栏含 Gradebook 入口——学生端 6 项、教师端 7 项） */
    val isTeacher: Boolean = false
) : Parcelable

/** 班级页子 Tab（对齐 iOS ClassTab；path = 官网 URL 路径段） */
enum class ClassTab(val path: String, val title: String) {
    COURSE("course", "Course"),
    CLASS_LIST("classlist", "Class List"),
    ATTENDANCE("attendance", "Attendance"),
    ASSIGNMENTS("assignments", "Assignments"),
    /** 注意 Assessments 的官网路径是 evaluation */
    ASSESSMENTS("evaluation", "Assessments"),
    /** 教师端独有第 7 项（学生端隐藏，见 ClassCourseActivity.visibleTabs） */
    GRADEBOOK("gradebook", "Gradebook"),
    CLASS_INFO("classinfo", "Class Info");

    companion object {
        fun fromPath(path: String): ClassTab? = entries.firstOrNull { it.path == path }
    }
}

/**
 * 教师端 Class List 成员行（表格视图：头像 / 姓名 / Id / Email，
 * 对齐 iOS TeacherClassListMember）
 */
data class TeacherClassListMember(
    /** 学生 id（view=student 链接） */
    val id: String,
    val name: String,
    /** 学生 Id 列（官网当前显示为空） */
    val number: String,
    val email: String,
    val avatarURL: String?
)

/** 班级作业（Assignments 页表格行，学生只读视角，对齐 iOS ClassAssignment） */
data class ClassAssignment(
    /** 课时链接（含 #ex 锚点）作稳定 id，如 /sunshine/lesson/1339/13233#ex4421 */
    val id: String,
    /** 状态文案（官网 data-tippy-content：Complete / Assigned...） */
    val status: String,
    /** 作业标题（课时名） */
    val title: String,
    /** 所属分组（如 Reading and Writing） */
    val section: String,
    /** 课时页链接（点击跳原生课时详情） */
    val lessonPath: String?,
    /** 截止日期（如 Oct 7th） */
    val dueDate: String,
    /** 得分（如 100%，未完成时为空） */
    val score: String
)

/** Class Info 页键值对（Class Code / Class Name / Course / Teacher，对齐 iOS ClassInfoItem） */
data class ClassInfoItem(
    val label: String,
    val value: String
)
