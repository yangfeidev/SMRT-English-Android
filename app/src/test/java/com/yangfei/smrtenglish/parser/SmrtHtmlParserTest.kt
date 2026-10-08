package com.yangfei.smrtenglish.parser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 官网页面解析回归测试。
 *
 * 夹具为从 smrtenglish.cn 抓取的真实 HTML（见 src/test/resources），
 * 用于在官网改版时第一时间发现解析规则失效。
 */
class SmrtHtmlParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader!!.getResourceAsStream(name)!!.bufferedReader().readText()

    @Test
    fun cafe_page_should_parse_videos() {
        val html = fixture("cafe_page1.html")
        val videos = SMRTHTMLParser.parseCafeVideos(html, "/sunshine/cafe")

        println("Cafe videos = ${videos.size}")
        videos.take(3).forEach {
            println(
                "  id=${it.id} title=${it.title} thumb=${it.thumbnailURL} " +
                    "video=${it.videoURL} cats=${it.categories.map { c -> c.name }}"
            )
        }
        assertTrue("Cafe 列表应解析出视频", videos.isNotEmpty())
        assertTrue("标题不应为空", videos.all { it.title.isNotEmpty() })
    }

    @Test
    fun library_home_should_parse_types_and_groups() {
        val html = fixture("library_home.html")
        val home = SMRTHTMLParser.parseLibraryHome(html)

        println("types = ${home.types.map { it.key to it.name }}")
        println("groups = ${home.groups.map { it.key to "${it.title}(${it.courses.size})" }}")

        assertTrue("应解析出类型卡", home.types.isNotEmpty())
        assertTrue("应解析出类型分组", home.groups.isNotEmpty())
        assertTrue("每个分组应有课程卡", home.groups.all { it.courses.isNotEmpty() })
    }

    @Test
    fun course_list_should_parse_courses() {
        val html = fixture("library_youth.html")
        val courses = SMRTHTMLParser.parseCourses(html)

        println("courses = ${courses.size}")
        courses.take(5).forEach {
            println("  id=${it.id} code=${it.code} title=${it.title} img=${it.imageURL}")
        }
        assertTrue("课程列表应解析出课程", courses.isNotEmpty())
    }

    @Test
    fun my_classes_should_parse_class_and_members() {
        val html = fixture("classes_sample.html")
        val classes = SMRTHTMLParser.parseMyClasses(html)

        classes.forEach { klass ->
            println(
                "班级 id=${klass.id} name=${klass.name} course=${klass.course} " +
                    "members=${klass.members.map { "${it.name}->${it.avatarURL}" }}"
            )
        }

        assertTrue("应解析出班级", classes.isNotEmpty())
        val klass = classes.first()
        assertTrue("班级名不应为空", klass.name.isNotEmpty())
        assertTrue("课程名不应为空", klass.course.isNotEmpty())
        assertTrue("应解析出成员头像（data-tippy-content + style）", klass.members.isNotEmpty())
        assertTrue("成员名应转为中文「姓+名」", klass.members.any { it.name == "张欣夏" })
        assertTrue("成员头像地址应解析成功", klass.members.all { !it.avatarURL.isNullOrEmpty() })
    }

    @Test
    fun course_detail_should_parse_unit_tree() {
        val html = fixture("library_course_346.html")
        val units = SMRTHTMLParser.parseCourseUnits(html)

        println("units = ${units.size}")
        units.forEach { unit ->
            println(
                "  单元 ${unit.number} ${unit.name} img=${unit.imageURL} " +
                    "分组=${unit.sections.map { s -> "${s.title}(${s.lessons.size})" }}"
            )
        }
        assertTrue("课程页应解析出单元", units.isNotEmpty())
        assertTrue("单元下应有课时", units.any { unit -> unit.sections.any { it.lessons.isNotEmpty() } })
        assertFalse(
            "课时标题不应全为空",
            units.flatMap { it.sections }.flatMap { it.lessons }.all { it.title.isEmpty() }
        )
    }
}
