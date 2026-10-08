package com.yangfei.smrtenglish.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/** Library 课程（封面/编号/描述） */
@Parcelize
data class LibraryCourse(
    /** 课程数字 id，如 384 */
    val id: String = "",
    val title: String = "",
    /** 课程代号，如 EC / EE1 / EE2 */
    val code: String = "",
    val courseDescription: String = "",
    val imageURL: String? = null
) : Parcelable

/** Library 首页 "English Courses" 区的类型卡 */
@Parcelize
data class LibraryTypeCard(
    val name: String = "",
    /** 类型 key：youth / kids / children（对应 ?s={key} 列表页） */
    val key: String = "",
    val imageURL: String? = null
) : Parcelable {
    val id: String get() = key
}

/** Library 首页课程封面卡（仅封面图，点击进课程详情） */
@Parcelize
data class LibraryCourseCard(
    val id: String = "",
    val imageURL: String? = null
) : Parcelable

/** Library 首页类型分组（标题 + 横滑课程卡） */
@Parcelize
data class LibraryGroup(
    val title: String = "",
    val key: String = "",
    val courses: List<LibraryCourseCard> = emptyList()
) : Parcelable {
    val id: String get() = key
}

/** Library 首页整页数据 */
data class LibraryHome(
    val types: List<LibraryTypeCard> = emptyList(),
    val groups: List<LibraryGroup> = emptyList()
)

/** 课时条目 */
@Parcelize
data class LessonItem(
    /** lessonId（如 13927）；未解锁课时无 id，用 "locked-{number}" 伪 id */
    val id: String = "",
    /** 课程 id（如 1586，lesson URL 第一段） */
    val courseId: String = "",
    /** 课时编号，如 "1-1" */
    val number: String = "",
    val title: String = "",
    /** 未解锁课时（页面 disabled-lesson，无链接） */
    val locked: Boolean = false
) : Parcelable

/** 课时分组（如 Conversation and Practice / Reading） */
@Parcelize
data class LessonSection(
    val title: String = "",
    val lessons: List<LessonItem> = emptyList()
) : Parcelable {
    val id: String get() = title
}

/** 课程单元（My Friends 01，内含多个分组） */
@Parcelize
data class CourseUnit(
    val name: String = "",
    val number: String = "",
    val imageURL: String? = null,
    val sections: List<LessonSection> = emptyList()
) : Parcelable {
    val id: String get() = name + number
}
