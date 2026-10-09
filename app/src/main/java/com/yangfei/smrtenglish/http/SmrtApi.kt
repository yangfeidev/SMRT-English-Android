package com.yangfei.smrtenglish.http

import com.blankj.utilcode.util.LogUtils
import com.yangfei.smrtenglish.base.manager.SessionManager
import com.yangfei.smrtenglish.bean.CafePage
import com.yangfei.smrtenglish.bean.CafeTab
import com.yangfei.smrtenglish.bean.ClassCourse
import com.yangfei.smrtenglish.bean.ClassTab
import com.yangfei.smrtenglish.bean.CourseUnit
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.bean.LibraryHome
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.parser.SMRTHTMLParser
import com.yangfei.smrtenglish.util.EventBusUtil
import com.yangfei.smrtenglish.util.SessionEvent

/**
 * 业务接口封装（对齐 iOS SMRTClient）
 *
 * 已实测验证的接口事实：
 * - 登录：POST https://smrtenglish.cn/process.php
 *     表单字段: email / password / sublogin=1 / school_key=sunshine
 *     成功返回 302 → /sunshine/cafe，并种下 PHPSESSID + cookname + cookid 三个 cookie
 * - 会话判断：登录后页面头部不再出现 "Create Account" 入口
 * - 内容页均为服务端渲染 HTML，媒体直连公开 CDN（hw-cdn.smrtenglish.cn）
 */
object SmrtApi {

    // MARK: - 登录 / 会话

    /** 登录。成功返回 true；账号密码错误抛 SmrtException.loginFailed */
    suspend fun login(email: String, password: String): Boolean {
        val result = Request.postForm(
            UrlConstant.PROCESS,
            mapOf(
                "email" to email,
                "password" to password,
                "sublogin" to "1",
                "school_key" to UrlConstant.SCHOOL_KEY
            )
        )
        // 302 → /sunshine/cafe 表示登录成功；否则服务端回渲染登录页
        if (result.finalUrl.contains(UrlConstant.CAFE) && !isLoggedOutHTML(result.html)) {
            LogUtils.i(UrlConstant.HTTP_LOG, "login success")
            return true
        }
        throw SmrtException.loginFailed()
    }

    /** 检查已有 cookie 会话是否有效 */
    suspend fun checkSession(): Boolean {
        return try {
            // requirePathContains：会话失效时 302 → /smrt/ 营销首页，靠最终路径判定
            val html = Request.getHtml(
                UrlConstant.LIBRARY,
                requirePathContains = UrlConstant.LIBRARY
            )
            !isLoggedOutHTML(html)
        } catch (e: Exception) {
            false
        }
    }

    /** 忘记密码：请求官网向邮箱发送重置邮件（POST subforgot=1） */
    suspend fun forgotPassword(email: String) {
        val result = Request.postForm(
            UrlConstant.PROCESS,
            mapOf(
                "email" to email,
                "subforgot" to "1",
                "school_key" to UrlConstant.SCHOOL_KEY
            )
        )
        if (result.code !in 200..299) throw SmrtException.badResponse()
        val html = result.html.lowercase()
        if (html.contains("alert-danger") || html.contains("no account")) {
            throw SmrtException.loginFailed()
        }
    }

    /** 页面是否为「未登录」状态（头部出现 Create Account 入口） */
    fun isLoggedOutHTML(html: String): Boolean =
        html.contains(">Create Account<") || html.contains("Create Account</a>")

    // MARK: - Cafe

    /**
     * Cafe 视频流（分页，游客态）。
     * 分页规则（实测）：?page=N，末页时分页控件 Next 项带 class="disabled"。
     */
    suspend fun cafeVideos(tab: CafeTab, page: Int): CafePage {
        val separator = if (tab.path.contains("?")) "&" else "?"
        val html = Request.getHtml("${tab.path}${separator}page=$page", asGuest = true)
        val videos = SMRTHTMLParser.parseCafeVideos(html, tab.path)
        if (videos.isEmpty()) throw SmrtException.parseFailed()
        val hasPagination = html.contains("pagination")
        val nextDisabled = html.contains("<li class=\"disabled\"><a href=\"#\">Next</a>")
        return CafePage(videos, hasPagination && !nextDisabled)
    }

    // MARK: - Library

    suspend fun courses(type: String): List<LibraryCourse> {
        val html = Request.getHtml(UrlConstant.libraryType(type))
        val courses = SMRTHTMLParser.parseCourses(html)
        if (courses.isEmpty()) throw SmrtException.parseFailed()
        return courses
    }

    suspend fun libraryHome(): LibraryHome {
        val html = Request.getHtml(UrlConstant.LIBRARY)
        val home = SMRTHTMLParser.parseLibraryHome(html)
        if (home.groups.isEmpty()) throw SmrtException.parseFailed()
        return home
    }

    suspend fun courseUnits(courseId: String): List<CourseUnit> {
        val html = Request.getHtml(UrlConstant.libraryCourse(courseId))
        val units = SMRTHTMLParser.parseCourseUnits(html)
        if (units.isEmpty()) throw SmrtException.parseFailed()
        return units
    }

    // MARK: - 会话失效自动恢复（对齐 iOS SMRTClient.withSessionRecovery）

    /**
     * 会话失效自动恢复包装：登录态请求抛 notLoggedIn 时，用记住的账密
     * 静默重登一次并重试原请求。
     * - 重登成功 → 用户无感恢复，数据正常返回
     * - 重登失败（无记住密码/密码已改）→ 会话确定失效，广播 TYPE_INVALID
     *   让 MainActivity 全局切登录页——等效用户手动「退出登录→再登录」，避免
     *   Classes 页拿缓存假登录态撑着、点进列表才失败的死局。
     * 所有登录态抓取方法（myClasses/classCourse/classPageHTML/refreshMyDisplayName）
     * 内部均已包上，调用方无需自行处理重登。
     */
    private suspend fun <T> withSessionRecovery(operation: suspend () -> T): T {
        try {
            return operation()
        } catch (e: SmrtException) {
            if (!e.isNotLoggedIn()) throw e
            LogUtils.i(UrlConstant.HTTP_LOG, "session expired, trying silent relogin")
            if (!autoReloginIfNeeded()) {
                LogUtils.e(UrlConstant.HTTP_LOG, "silent relogin failed, broadcasting session invalid")
                EventBusUtil.post(SessionEvent(SessionEvent.TYPE_INVALID))
                throw e
            }
            LogUtils.i(UrlConstant.HTTP_LOG, "silent relogin ok, retrying request")
            return operation()
        }
    }

    // MARK: - Classes（我的班级）

    /** 我的班级列表（登录态）。新账号无班级时返回空数组。 */
    /** 内部含会话恢复：失效先静默重登再重试，重登失败广播切登录页。 */
    suspend fun myClasses(): List<StudentClass> = withSessionRecovery {
        val html = Request.getHtml(
            UrlConstant.CLASSES,
            requirePathContains = UrlConstant.CLASSES
        )
        if (isLoggedOutHTML(html)) throw SmrtException.notLoggedIn()
        // 顺手判定账号角色：学生号班级卡带「我」角标，教师号没有（零额外请求）
        SessionManager.updateAccountRole(!SMRTHTMLParser.classesPageIndicatesStudent(html))
        SMRTHTMLParser.parseMyClasses(html)
    }

    /** 班级课程页（Course tab，单元树）。内部含会话恢复（同 myClasses）。 */
    suspend fun classCourse(classId: String): ClassCourse = withSessionRecovery {
        val html = Request.getHtml(
            UrlConstant.classCourse(classId),
            requirePathContains = "/sunshine/class/"
        )
        if (isLoggedOutHTML(html)) throw SmrtException.notLoggedIn()
        val course = SMRTHTMLParser.parseClassCourse(html)
        if (course.units.isEmpty()) throw SmrtException.parseFailed()
        // 角色硬校正：班级页侧栏 gradebook 链接是教师铁证
        // （classes 页「Join a Class」入口判定兜底，打开班级后以此为准）
        SessionManager.updateAccountRole(course.isTeacher)
        course
    }

    /** 班级子页面 HTML（Class List / Attendance / Assignments / Assessments / Class Info）。 */
    /** 内部含会话恢复（同 myClasses）。 */
    suspend fun classPageHTML(classId: String, tab: ClassTab): String =
        classPageHTML(UrlConstant.classPage(classId, tab.path))

    /**
     * 任意官网路径抓 HTML（登录态，含会话恢复）。
     * 教师端 Class List 的 ?tab=current-students/removed-students 子页用。
     */
    suspend fun classPageHTML(path: String): String = withSessionRecovery {
        val html = Request.getHtml(
            path,
            requirePathContains = "/sunshine/class/"
        )
        if (isLoggedOutHTML(html)) throw SmrtException.notLoggedIn()
        html
    }

    /** 会话失效后用记住的账密静默重登（用户主动退出过的账号不重登） */
    suspend fun autoReloginIfNeeded(): Boolean {
        if (SessionManager.loggedOutByUser) return false
        val email = SessionManager.email
        val password = SessionManager.password
        if (email.isNullOrEmpty() || password.isNullOrEmpty()) return false
        return try {
            login(email, password)
        } catch (e: Exception) {
            false
        }
    }

    // MARK: - 当前账号显示名

    /** 拉取并缓存当前账号显示名（Me 页 name 字段，西式「名 姓」转中式）。失败回退缓存。 */
    suspend fun refreshMyDisplayName(): String? {
        val html = try {
            withSessionRecovery {
                Request.getHtml(UrlConstant.ME, requirePathContains = UrlConstant.ME)
            }
        } catch (e: Exception) {
            return SessionManager.displayName
        }
        if (isLoggedOutHTML(html)) return SessionManager.displayName
        val name = SMRTHTMLParser.parseMyDisplayName(html)
        if (!name.isNullOrEmpty()) {
            val normalized = SMRTHTMLParser.normalizeChineseName(name)
            SessionManager.displayName = normalized
            return normalized
        }
        return SessionManager.displayName
    }
}
