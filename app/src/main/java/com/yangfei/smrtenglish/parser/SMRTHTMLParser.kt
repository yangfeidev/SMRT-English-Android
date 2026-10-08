package com.yangfei.smrtenglish.parser

import com.yangfei.smrtenglish.bean.CafeCategory
import com.yangfei.smrtenglish.bean.CafeVideo
import com.yangfei.smrtenglish.bean.ClassCourse
import com.yangfei.smrtenglish.bean.ClassLesson
import com.yangfei.smrtenglish.bean.ClassMember
import com.yangfei.smrtenglish.bean.ClassSection
import com.yangfei.smrtenglish.bean.ClassUnit
import com.yangfei.smrtenglish.bean.CourseUnit
import com.yangfei.smrtenglish.bean.LessonItem
import com.yangfei.smrtenglish.bean.LessonSection
import com.yangfei.smrtenglish.bean.LibraryCourse
import com.yangfei.smrtenglish.bean.LibraryCourseCard
import com.yangfei.smrtenglish.bean.LibraryGroup
import com.yangfei.smrtenglish.bean.LibraryHome
import com.yangfei.smrtenglish.bean.LibraryTypeCard
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.bean.ClassAssignment
import com.yangfei.smrtenglish.bean.ClassInfoItem
import java.util.Locale

/**
 * 官网页面解析器（对齐 iOS SMRTHTMLParser）
 *
 * smrtenglish.cn 为服务端渲染页面，结构规整，这里用「标记扫描 + 正则提取」解析：
 * 1. Cafe 视频流     /sunshine/cafe[/levels]
 * 2. Library 课程列表 /sunshine/library?s={type}
 * 3. Library 首页     /sunshine/library
 * 4. 课程页（单元树）  /sunshine/library/{courseId}
 * 5. 我的班级        /sunshine/classes
 * 6. 班级课程页      /sunshine/class/{classId}/course/
 */
object SMRTHTMLParser {

    private val LEVELS = setOf("adult", "youth", "kids", "children")

    /** Cafe 卡片分隔串：真实 HTML 为 <div class="blog col-md-4 col-sm-6">，注意结尾不要带引号 */
    private const val CARD_MARKER = "<div class=\"blog "
    private const val UNIT_MARKER = "<div class=\"lesson-unit row\">"
    private const val CLASS_UNIT_MARKER = "<div class=\"unit-section\">"

    // MARK: - 1. Cafe 视频流

    fun parseCafeVideos(html: String, levelPath: String): List<CafeVideo> {
        val videos = ArrayList<CafeVideo>()
        val positions = HtmlUtil.indexesOf(CARD_MARKER, html)
        if (positions.isEmpty()) return emptyList()

        for (i in positions.indices) {
            val start = positions[i]
            val end = if (i + 1 < positions.size) positions[i + 1] else html.length
            val chunk = html.substring(start, end)
            if (!chunk.contains("videothumb")) continue

            val modalId = HtmlUtil.firstGroup("data-src=\"#(\\d+)\"", chunk)
            val thumb = HtmlUtil.firstGroup("""url\((https://[^)]+?\.jpg)\)""", chunk)
            val title = HtmlUtil.firstGroup("""<h3>([\s\S]*?)</h3>""", chunk)?.let {
                HtmlUtil.stripTags(it)
            } ?: ""
            if (title.isEmpty()) continue
            val desc = HtmlUtil.firstGroup("<div class=\"content\">([\\s\\S]*?)</div>", chunk)?.let {
                HtmlUtil.stripTags(it)
            } ?: ""

            // 分类标签：class("category adult"——第一个常为级别标签) + href + 文本
            val categories = HtmlUtil.matches(
                "<a class=\"category([^\"]*)\" href=\"([^\"]*)\">([\\s\\S]*?)</a>", chunk
            ).mapNotNull { m ->
                val classRest = m[1] ?: return@mapNotNull null
                val path = m[2] ?: return@mapNotNull null
                val rawName = m[3] ?: return@mapNotNull null
                val n = HtmlUtil.stripTags(rawName)
                if (n.isEmpty()) return@mapNotNull null
                val levelPart = classRest.trim()
                CafeCategory(
                    name = n,
                    path = HtmlUtil.decodeEntities(path),
                    level = if (levelPart in LEVELS) levelPart else null
                )
            }

            var videoURL: String? = null
            var audioURL: String? = null

            // 优先：modal 容器里的 flowplayer source
            if (modalId != null) {
                val modalHead = "<div id=\"$modalId\""
                val modalStart = html.indexOf(modalHead)
                if (modalStart >= 0) {
                    val from = modalStart + modalHead.length
                    val tail = html.indexOf("</div>\n</div>", from)
                    val modal = html.substring(from, if (tail < 0) html.length else tail)

                    val src = HtmlUtil.firstGroup(
                        "<source[^>]*type=\"video/mp4\"[^>]*src=\"([^\"]+)\"", modal
                    ) ?: HtmlUtil.firstGroup(
                        "<source[^>]*src=\"([^\"]+)\"[^>]*type=\"video/mp4\"", modal
                    ) ?: HtmlUtil.firstGroup(
                        "<source[^>]*src=\"([^\"]+\\.mp4[^\"]*)\"", modal
                    )
                    if (!src.isNullOrEmpty()) videoURL = HtmlUtil.decodeEntities(src)

                    // 视频兜底 1：<video src="..."> 属性直挂
                    if (videoURL == null) {
                        videoURL = HtmlUtil.firstGroup("<video[^>]*src=\"([^\"]+)\"", modal)
                            ?.let { HtmlUtil.decodeEntities(it) }
                    }
                    // 视频兜底 2：弹窗区域内任意 .mp4 直链
                    if (videoURL == null) {
                        videoURL = HtmlUtil.firstGroup("(https://[^\"\\s]+?\\.mp4[^\"\\s]*)", modal)
                            ?.let { HtmlUtil.decodeEntities(it) }
                    }
                    // 音频兜底：<audio src>
                    if (videoURL == null) {
                        audioURL = HtmlUtil.firstGroup("<audio[^>]*src=\"([^\"]+)\"", modal)
                            ?.let { HtmlUtil.decodeEntities(it) }
                            ?: HtmlUtil.firstGroup(
                                "<audio[\\s\\S]*?<source[^>]*src=\"([^\"]+)\"", modal
                            )?.let { HtmlUtil.decodeEntities(it) }
                    }
                }
            }
            // 兜底：缩略图 vid 号 = 视频文件名（实测同编号规律）
            if (videoURL.isNullOrEmpty() && thumb != null && thumb.contains("/vid")) {
                videoURL = thumb.replace(".jpg", ".mp4")
            }

            videos.add(
                CafeVideo(
                    id = modalId ?: System.nanoTime().toString(),
                    title = title,
                    description = desc,
                    categories = categories,
                    thumbnailURL = thumb,
                    videoURL = videoURL,
                    audioURL = audioURL,
                    levelPath = levelPath
                )
            )
        }
        return videos
    }

    // MARK: - 2. Library 课程列表

    fun parseCourses(html: String): List<LibraryCourse> {
        val courses = ArrayList<LibraryCourse>()
        val pattern = "<a href=\"/sunshine/library/(\\d+)\">[\\s\\S]*?data-src=\"([^\"]+)\"[\\s\\S]*?" +
            "<h4 class=\"heading\">([\\s\\S]*?)</h4>[\\s\\S]*?<p>([\\s\\S]*?)</p>"
        for (m in HtmlUtil.matches(pattern, html)) {
            val id = m[1] ?: continue
            val img = m[2] ?: continue
            val heading = m[3] ?: continue
            val desc = m[4] ?: continue
            val code = HtmlUtil.firstGroup("<span class=\"code\">([^<]*)</span>", heading) ?: ""
            val noCode = heading.replace(Regex("<span class=\"code\">[^<]*</span>"), "")
            courses.add(
                LibraryCourse(
                    id = id,
                    title = HtmlUtil.stripTags(noCode),
                    code = code,
                    courseDescription = HtmlUtil.stripTags(desc),
                    imageURL = HtmlUtil.decodeEntities(img)
                )
            )
        }
        return courses
    }

    // MARK: - 2b. Library 首页

    fun parseLibraryHome(html: String): LibraryHome {
        val types = HtmlUtil.matches(
            "<a href=\"/sunshine/library\\?s=(\\w+)\">\\s*<div class=\"typeimg\">\\s*" +
                "<img[^>]*data-src=\"([^\"]+)\"[^>]*>\\s*<p>([^<]*)</p>",
            html
        ).mapNotNull { m ->
            val key = m[1] ?: return@mapNotNull null
            val img = m[2] ?: return@mapNotNull null
            val name = m[3] ?: return@mapNotNull null
            LibraryTypeCard(
                name = HtmlUtil.stripTags(name),
                key = key,
                imageURL = HtmlUtil.decodeEntities(img)
            )
        }

        val groups = ArrayList<LibraryGroup>()
        val groupPattern = "<div class=\"title\"><a href=\"[^\"]*\\?s=(\\w+)\"><h3>([\\s\\S]*?)</h3>" +
            "</a></div>([\\s\\S]*?)(?=<div class=\"title\">|<footer)"
        for (m in HtmlUtil.matches(groupPattern, html)) {
            val key = m[1] ?: continue
            val titleHTML = m[2] ?: continue
            val body = m[3] ?: continue
            val courses = HtmlUtil.matches(
                "<a href=\"/sunshine/library/(\\d+)\">\\s*<img[^>]*data-src=\"([^\"]+)\"",
                body
            ).mapNotNull { cm ->
                val cid = cm[1] ?: return@mapNotNull null
                val img = cm[2] ?: return@mapNotNull null
                LibraryCourseCard(id = cid, imageURL = HtmlUtil.decodeEntities(img))
            }
            if (courses.isEmpty()) continue
            groups.add(
                LibraryGroup(
                    title = HtmlUtil.stripTags(titleHTML),
                    key = key,
                    courses = courses
                )
            )
        }
        return LibraryHome(types = types, groups = groups)
    }

    // MARK: - 3. 课程页（单元树）

    fun parseCourseUnits(html: String): List<CourseUnit> {
        val unitsRegion = HtmlUtil.region("<div id=\"units\">", "<div id=\"mobile-", html)
        if (unitsRegion.isEmpty()) return emptyList()
        val positions = HtmlUtil.indexesOf(UNIT_MARKER, unitsRegion)
        if (positions.isEmpty()) return emptyList()

        val units = ArrayList<CourseUnit>()
        for (i in positions.indices) {
            val start = positions[i]
            val end = if (i + 1 < positions.size) positions[i + 1] else unitsRegion.length
            val chunk = unitsRegion.substring(start, end)

            val name = HtmlUtil.firstGroup("<span class=\"unitdesc\">([\\s\\S]*?)</span>", chunk)?.let {
                HtmlUtil.stripTags(it)
            } ?: "Unit ${i + 1}"
            val number = HtmlUtil.firstGroup("<span class=\"unitnumber\">([\\s\\S]*?)</span>", chunk)
                ?: String.format(Locale.US, "%02d", i + 1)
            val photo = HtmlUtil.firstGroup(
                "unitphoto[^>]*background-image:\\s*url\\('([^']+)'[\"\\)]", chunk
            )

            val sections = ArrayList<LessonSection>()
            var currentTitle = ""
            val currentLessons = ArrayList<LessonItem>()

            // 顺序扫描 category-header / 可用课时 / 未解锁课时（disabled-lesson 无链接）
            val tokenPattern = "<div class=\"category-header\"><h4>([\\s\\S]*?)</h4></div>|" +
                "<div class=\"lesson-section\"><a href=\"/sunshine/lesson/(\\d+)/(\\d+)\"[^>]*>" +
                "[\\s\\S]*?<span class=\"lessonnum\">([\\s\\S]*?)</span>([\\s\\S]*?)</span>|" +
                "<div class=\"lesson-section\"><div class=\"lesson-link disabled-lesson\">" +
                "[\\s\\S]*?<span class=\"lessonnum\">([\\s\\S]*?)</span>([\\s\\S]*?)</span>"
            for (m in HtmlUtil.matches(tokenPattern, chunk)) {
                val sectionTitle = m[1]
                val cid = m[2]
                val lid = m[3]
                val num = m[4]
                val lessonTitle = m[5]
                val lockedNum = m[6]
                val lockedTitle = m[7]
                when {
                    sectionTitle != null -> {
                        flushLessonSection(sections, currentTitle, currentLessons)
                        currentTitle = HtmlUtil.stripTags(sectionTitle)
                    }
                    cid != null && lid != null && num != null && lessonTitle != null -> {
                        currentLessons.add(
                            LessonItem(
                                id = lid,
                                courseId = cid,
                                number = HtmlUtil.stripTags(num),
                                title = HtmlUtil.stripTags(lessonTitle)
                            )
                        )
                    }
                    lockedNum != null && lockedTitle != null -> {
                        val numStr = HtmlUtil.stripTags(lockedNum)
                        currentLessons.add(
                            LessonItem(
                                id = "locked-$numStr",
                                courseId = "",
                                number = numStr,
                                title = HtmlUtil.stripTags(lockedTitle),
                                locked = true
                            )
                        )
                    }
                }
            }
            flushLessonSection(sections, currentTitle, currentLessons)

            units.add(
                CourseUnit(
                    name = name,
                    number = number,
                    imageURL = photo,
                    sections = sections
                )
            )
        }
        return units
    }

    private fun flushLessonSection(
        sections: MutableList<LessonSection>,
        title: String,
        lessons: MutableList<LessonItem>
    ) {
        if (lessons.isEmpty()) return
        sections.add(LessonSection(title = title.ifEmpty { "课时" }, lessons = ArrayList(lessons)))
        lessons.clear()
    }

    // MARK: - 5. 我的班级（/sunshine/classes）

    /**
     * 官网成员名为西式「名 姓」顺序（如 谨灿 杨），转为中式「姓+名」（杨谨灿）。
     * 仅当两段均为汉字时才交换，英文名（Jim Clark）原样保留。
     */
    fun normalizeChineseName(s: String): String {
        val parts = s.split(" ").filter { it.isNotEmpty() }
        if (parts.size != 2) return s
        if (isCJK(parts[0]) && isCJK(parts[1])) return parts[1] + parts[0]
        return s
    }

    private fun isCJK(str: String): Boolean {
        if (str.isEmpty()) return false
        return str.all { it.code in 0x4E00..0x9FFF }
    }

    /** 解析 Me 页当前账号显示名（<input name="name" type="text" value="谨灿 杨">） */
    fun parseMyDisplayName(html: String): String? {
        val raw = HtmlUtil.firstGroup("name=\"name\"[^>]*value=\"([^\"]*)\"", html) ?: return null
        val name = HtmlUtil.decodeEntities(raw).trim()
        return name.ifEmpty { null }
    }

    /** 解析 Classes 列表页。新账号可能没有任何班级，返回空数组属正常情况。 */
    fun parseMyClasses(html: String): List<StudentClass> {
        val region = HtmlUtil.region("id=\"classes\"", "<footer", html)
        if (region.isEmpty()) return emptyList()

        val classes = ArrayList<StudentClass>()
        val linkMatches = HtmlUtil.matches("<a href=\"/sunshine/class/(\\d+)/course/\">", region)

        for (index in linkMatches.indices) {
            val link = linkMatches[index]
            val id = link[1] ?: continue
            val chunkStart = link.start
            val chunkEnd = if (index + 1 < linkMatches.size) linkMatches[index + 1].start else region.length
            val chunk = region.substring(chunkStart, chunkEnd)

            val name = HtmlUtil.stripTags(
                HtmlUtil.firstGroup("<h2[^>]*>([\\s\\S]*?)</h2>", chunk) ?: ""
            )
            val course = HtmlUtil.stripTags(
                HtmlUtil.firstGroup("class=\"coursepill\"[^>]*>([\\s\\S]*?)</span>", chunk) ?: ""
            )

            val members = ArrayList<ClassMember>()
            // 对齐 iOS：#"class="piclittle classes"[^>]*"#，匹配整个头像标签以取出
            // data-tippy-content（成员名）与 style（头像图）
            for (m in HtmlUtil.matches("class=\"piclittle classes\"[^>]*", chunk)) {
                val tag = m.full
                val memberName = HtmlUtil.attr("data-tippy-content", tag)
                if (memberName.isNullOrEmpty()) continue
                val style = HtmlUtil.attr("style", tag)
                // 取捕获组 2 = 真实地址（iOS 取到的是组 1 的引号，导致头像永远加载失败，
                // 这里修正为取地址本身）：url('xxx') 与 url(xxx) 两种写法都要覆盖
                val avatarPath = style?.let {
                    HtmlUtil.matches("url\\((['\"]?)([^'\")]+)\\1\\)", it).firstOrNull()?.get(2)
                }
                members.add(
                    ClassMember(
                        id = memberName,
                        name = normalizeChineseName(memberName),
                        avatarURL = avatarPath
                    )
                )
            }

            // 服务端成员顺序不稳定，本地按姓名固定排序，并生成与顺序绑定的稳定 id
            // （用 Collator 做本地化排序，对齐 iOS 的 localizedStandardCompare）
            val collator = java.text.Collator.getInstance()
            val sortedMembers = members
                .sortedWith(compareBy(collator) { it.name })
                .mapIndexed { i, member ->
                    ClassMember(id = "$i-${member.name}", name = member.name, avatarURL = member.avatarURL)
                }

            classes.add(
                StudentClass(
                    id = id,
                    name = name,
                    course = course,
                    coursePath = "/sunshine/class/$id/course/",
                    members = sortedMembers
                )
            )
        }
        return classes
    }

    // MARK: - 6. 班级课程页（/sunshine/class/{classId}/course/）

    fun parseClassCourse(html: String): ClassCourse {
        val className = HtmlUtil.stripTags(
            HtmlUtil.firstGroup("classtext[^>]*>\\s*<h3[^>]*>([\\s\\S]*?)</h3>", html) ?: ""
        )
        val courseName = HtmlUtil.stripTags(
            HtmlUtil.firstGroup("<h1 class=\"page-title\"><a[^>]*>([\\s\\S]*?)</a>", html) ?: ""
        )
        val courseLibraryId = HtmlUtil.firstGroup("href=\"/sunshine/library/(\\d+)\"", html) ?: ""

        val accordion = HtmlUtil.region("id=\"accordion\"", "<div id=\"mobile-", html)
        val positions = HtmlUtil.indexesOf(CLASS_UNIT_MARKER, accordion)

        val units = ArrayList<ClassUnit>()
        for (i in positions.indices) {
            val start = positions[i]
            val end = if (i + 1 < positions.size) positions[i + 1] else accordion.length
            val chunk = accordion.substring(start, end)

            val titleChunk = HtmlUtil.firstGroup(
                "<h3 class=\"unit-title\">([\\s\\S]*?)</h3>", chunk
            ) ?: ""
            val imageURL = HtmlUtil.firstGroup(
                "class=\"unit-image\"[^>]*background-image:\\s*url\\(([^)]+)\\)", titleChunk
            )?.let { HtmlUtil.decodeEntities(it.trim()) }
            val number = HtmlUtil.stripTags(
                HtmlUtil.firstGroup(
                    "<span class=\"unit-number-right\">([\\s\\S]*?)</span>", titleChunk
                ) ?: ""
            )
            val name = HtmlUtil.stripTags(
                titleChunk
                    .replace(Regex("<span class=\"unit-image\"[\\s\\S]*?</span>"), "")
                    .replace(Regex("<span class=\"unit-number-right\">[\\s\\S]*?</span>"), "")
            )

            val contentStart = chunk.indexOf("unit-content")
            val content = if (contentStart >= 0) {
                chunk.substring(contentStart + "unit-content".length)
            } else chunk

            val sections = ArrayList<ClassSection>()
            var currentTitle = ""
            val currentLessons = ArrayList<ClassLesson>()

            val tokenPattern = "<div class=\"category-header\"><h4>([\\s\\S]*?)</h4></div>|" +
                "<a href=\"/sunshine/lesson/(\\d+)/(\\d+)\"[^>]*class=\"lesson-link\""
            val allMatches = Regex(tokenPattern).findAll(content).toList()
            for (index in allMatches.indices) {
                val m = allMatches[index]
                val sectionTitle = m.groups[1]?.value
                val cid = m.groups[2]?.value
                val lid = m.groups[3]?.value
                if (sectionTitle != null) {
                    flushClassSection(sections, currentTitle, currentLessons)
                    currentTitle = HtmlUtil.stripTags(sectionTitle)
                } else if (cid != null && lid != null) {
                    // 课时块从链接起点到下一个 token（分组头/下一课时），避免吞掉下一分组头
                    val linkStart = m.range.first
                    val next = if (index + 1 < allMatches.size) allMatches[index + 1].range.first else content.length
                    val lessonChunk = content.substring(linkStart, next)

                    val statusClass = HtmlUtil.firstGroup(
                        "class=\"lesson-status ([^\"]*)\"", lessonChunk
                    ) ?: ""
                    val status = HtmlUtil.stripTags(
                        HtmlUtil.firstGroup("data-tippy-content=\"([^\"]*)\"", lessonChunk) ?: ""
                    )
                    val num = HtmlUtil.stripTags(
                        HtmlUtil.firstGroup(
                            "<span class=\"lessonnum\">([\\s\\S]*?)</span>", lessonChunk
                        ) ?: ""
                    )
                    val lessonTitle = HtmlUtil.stripTags(
                        HtmlUtil.firstGroup(
                            "<span class=\"lesson-title\"><span class=\"lessonnum\">[\\s\\S]*?</span>" +
                                "([\\s\\S]*?)</span>",
                            lessonChunk
                        ) ?: ""
                    )
                    currentLessons.add(
                        ClassLesson(
                            id = lid,
                            courseId = cid,
                            number = num,
                            title = lessonTitle,
                            status = status,
                            hasExercises = statusClass.contains("has-exercises")
                        )
                    )
                }
            }
            flushClassSection(sections, currentTitle, currentLessons)

            val unitId = HtmlUtil.firstGroup("href=\"#unit(\\d+)\"", chunk) ?: i.toString()
            units.add(
                ClassUnit(
                    id = unitId,
                    name = name.ifEmpty { "Unit ${i + 1}" },
                    number = number,
                    imageURL = imageURL,
                    sections = sections
                )
            )
        }
        return ClassCourse(
            className = className,
            courseName = courseName,
            courseLibraryId = courseLibraryId,
            units = units
        )
    }

    private fun flushClassSection(
        sections: MutableList<ClassSection>,
        title: String,
        lessons: MutableList<ClassLesson>
    ) {
        if (lessons.isEmpty()) return
        sections.add(ClassSection(title = title.ifEmpty { "Lessons" }, lessons = ArrayList(lessons)))
        lessons.clear()
    }

    // MARK: - 6b. 班级子页面（Class List / Assignments / Attendance / Assessments / Class Info）
    //
    // 学生只读视角的 5 个低频页面，结构都很简单（对齐 iOS 同名解析函数）：
    // - classlist：p.miniprofile-title 成员名列表
    // - assignments：表格行 = 状态(tippy) + 标题(链接到课时#ex) + 截止日期 + 得分(resultgraph)
    // - attendance / assessments：当前为空态（No Attendance Records / No Assessment Results）
    // - classinfo：fieldset.profileform 内 label + form-control 键值对

    /** Class List 页成员名：官网为西式「名 姓」，转中式「姓+名」并按姓名排序 */
    fun parseClassListMembers(html: String): List<String> {
        val collator = java.text.Collator.getInstance()
        return HtmlUtil.matches("<p class=\"miniprofile-title\"[^>]*>([\\s\\S]*?)</p>", html)
            .mapNotNull { m ->
                val name = HtmlUtil.stripTags(m[1] ?: "")
                if (name.isEmpty()) null else name
            }
            .map { normalizeChineseName(it) }
            .sortedWith(compareBy(collator) { it })
    }

    /** Assignments 页作业行 */
    fun parseClassAssignments(html: String): List<ClassAssignment> {
        val tableStart = html.indexOf("<table")
        if (tableStart < 0) return emptyList()
        val tableEnd = html.indexOf("</table>", tableStart).let { if (it < 0) html.length else it }
        val table = html.substring(tableStart, tableEnd)

        val result = ArrayList<ClassAssignment>()
        for (rowMatch in HtmlUtil.matches("<tr[^>]*>([\\s\\S]*?)</tr>", table)) {
            val row = rowMatch[1] ?: continue
            // 行首 td 的 data-tippy-content 即状态（Complete / Assigned...）
            val status = HtmlUtil.firstGroup("data-tippy-content=\"([^\"]*)\"", row) ?: ""
            val titleRaw = HtmlUtil.stripTags(
                HtmlUtil.firstGroup("<strong[^>]*>([\\s\\S]*?)</strong>", row) ?: ""
            )
            if (titleRaw.isEmpty()) continue
            val link = HtmlUtil.firstGroup("href=\"(/sunshine/lesson/[^\"]+)\"", row)
            val section = HtmlUtil.stripTags(
                HtmlUtil.firstGroup("<span class=\"smalltext\">([\\s\\S]*?)</span>", row) ?: ""
            )
            // 截止日期：tippy 文案为 "Assigned Oct 6th" 的 span，正文是截止日
            val dueDate = HtmlUtil.stripTags(
                HtmlUtil.firstGroup(
                    "<span[^>]*data-tippy-content=\"Assigned[^\"]*\"[^>]*>([\\s\\S]*?)</span>", row
                ) ?: ""
            )
            // 得分：resultgraph 内文本（如 100%），未完成时可能为空
            val score = HtmlUtil.stripTags(
                HtmlUtil.firstGroup("<div class=\"resultgraph[^\"]*\">([\\s\\S]*?)</div>", row) ?: ""
            )
            result.add(
                ClassAssignment(
                    id = link ?: titleRaw,
                    status = status,
                    title = titleRaw,
                    section = section,
                    lessonPath = link,
                    dueDate = dueDate,
                    score = score
                )
            )
        }
        return result
    }

    /** Class Info 页键值对（Teacher 的值内含多段 <p>，以换行拼接） */
    fun parseClassInfo(html: String): List<ClassInfoItem> {
        return HtmlUtil.matches(
            "<label class=\"col-md-3\">([\\s\\S]*?)</label>[\\s\\S]*?<div class=\"form-control disabled\">([\\s\\S]*?)</div>",
            html
        ).mapNotNull { m ->
            val label = HtmlUtil.stripTags(m[1] ?: "")
            if (label.isEmpty()) return@mapNotNull null
            val rawValue = (m[2] ?: "").replace("</p>", "\n")
            // 多段文本间的大段空白收敛为单个换行
            val value = HtmlUtil.stripTags(rawValue)
                .split("\n")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString("\n")
                .trim()
            ClassInfoItem(label = label, value = value)
        }
    }

    /** Attendance 页是否为空态（官网文案 No Attendance Records） */
    fun parseClassAttendanceEmpty(html: String): Boolean = html.contains("No Attendance Records")

    /** Assessments 页是否为空态（官网文案 No Assessment Results） */
    fun parseClassAssessmentsEmpty(html: String): Boolean = html.contains("No Assessment Results")
}
