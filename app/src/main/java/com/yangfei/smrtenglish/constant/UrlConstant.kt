package com.yangfei.smrtenglish.constant

import com.yangfei.smrtenglish.BuildConfig

/**
 * 地址常量（对齐 iOS SMRTClient）
 *
 * 官网为服务端渲染 PHP 页面，无 JSON API，全部走页面路径。
 */
object UrlConstant {

    const val BASE_URL: String = BuildConfig.BASE_URL
    const val SCHOOL_KEY: String = BuildConfig.SCHOOL_KEY
    const val HTTP_LOG: String = BuildConfig.HTTP_LOG

    // ===================== 页面路径 =====================

    /** 登录 / 忘记密码（POST 表单） */
    const val PROCESS: String = "/process.php"

    /** 会话校验用（登录后 302 落点） */
    const val CAFE: String = "/sunshine/cafe"

    /** Library 首页 */
    const val LIBRARY: String = "/sunshine/library"

    /** 课程单元树：/sunshine/library/{courseId} */
    fun libraryCourse(courseId: String) = "$LIBRARY/$courseId"

    /** 课程类型列表：/sunshine/library?s={type} */
    fun libraryType(type: String) = "$LIBRARY?s=$type"

    /** 我的班级 */
    const val CLASSES: String = "/sunshine/classes"

    /** 班级课程页：/sunshine/class/{classId}/course/ */
    fun classCourse(classId: String) = "/sunshine/class/$classId/course/"

    /** 班级子页面：/sunshine/class/{classId}/{tab}/（tab 路径段见 ClassTab.path） */
    fun classPage(classId: String, tabPath: String) = "/sunshine/class/$classId/$tabPath/"

    /** 课时页：/sunshine/lesson/{courseId}/{lessonId} */
    fun lesson(courseId: String, lessonId: String) = "/sunshine/lesson/$courseId/$lessonId"

    /** 个人资料页 */
    const val ME: String = "/sunshine/me"

    fun fullUrl(path: String) = BASE_URL + path
}
