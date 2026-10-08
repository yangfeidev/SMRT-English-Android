package com.yangfei.smrtenglish.parser

/**
 * HTML 处理的正则小工具（对齐 iOS SMRTHTMLParser 的基础工具部分）
 */
object HtmlUtil {

    /** 正则匹配结果：完整匹配 + 全部捕获组 + 起始下标 */
    data class RegexMatch(
        val full: String,
        val groups: List<String?>,
        val start: Int
    ) {
        /** groups[0] 对应捕获组 1 */
        operator fun get(i: Int): String? = groups.getOrNull(i - 1)
    }

    private val namedEntities = mapOf(
        "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"",
        "apos" to "'", "nbsp" to " ", "#39" to "'", "hellip" to "…",
        "mdash" to "—", "ndash" to "–",
        "rsquo" to "’", "lsquo" to "‘"
    )

    /** 提取标签内属性值 */
    fun attr(name: String, tag: String): String? {
        val pattern = Regex("(?i)\\b${Regex.escape(name)}\\s*=\\s*(\"([^\"]*)\"|'([^']*)')")
        val m = pattern.find(tag) ?: return null
        return m.groups[2]?.value ?: m.groups[3]?.value
    }

    /** 去掉所有标签，仅保留文本 */
    fun stripTags(s: String): String {
        var result = s.replace(Regex("<br\\s*/?>"), " ")
        result = result.replace(Regex("<[^>]*>"), "")
        return decodeEntities(result).trim()
    }

    /** 解码常见 HTML 实体 */
    fun decodeEntities(s: String): String {
        var result = s
        namedEntities.forEach { (k, v) -> result = result.replace("&$k;", v) }
        val numeric = Regex("&#(\\d+);")
        while (true) {
            val m = numeric.find(result) ?: break
            val n = m.groups[1]?.value?.toLongOrNull()
            result = result.replaceRange(
                m.range,
                if (n != null && n in 0..0x10FFFF) String(Character.toChars(n.toInt())) else " "
            )
        }
        return result
    }

    /** 提取页面某标记之后的区域（到下一个标记或结尾） */
    fun region(after: String, until: String, html: String): String {
        val start = html.indexOf(after)
        if (start < 0) return ""
        val from = start + after.length
        val end = html.indexOf(until, from)
        return html.substring(from, if (end < 0) html.length else end)
    }

    /** 正则全部匹配（含捕获组） */
    fun matches(pattern: String, s: String): List<RegexMatch> {
        val regex = try {
            Regex(pattern)
        } catch (e: Exception) {
            return emptyList()
        }
        return regex.findAll(s).map { m ->
            RegexMatch(
                full = m.value,
                groups = (1 until m.groupValues.size).map { m.groups[it]?.value },
                start = m.range.first
            )
        }.toList()
    }

    /** 正则第一个匹配的捕获组 1 */
    fun firstGroup(pattern: String, s: String): String? =
        matches(pattern, s).firstOrNull()?.get(1)

    /** 非重叠查找子串下标 */
    fun indexesOf(needle: String, s: String): List<Int> {
        val result = ArrayList<Int>()
        var from = 0
        while (from <= s.length) {
            val idx = s.indexOf(needle, from)
            if (idx < 0) break
            result.add(idx)
            from = idx + needle.length
        }
        return result
    }
}
