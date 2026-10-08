package com.yangfei.smrtenglish.bean

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.net.URLEncoder

/**
 * Cafe 分类标签（可点击，点击后按该分类过滤列表）
 */
@Parcelize
data class CafeCategory(
    val name: String = "",
    /** 分类过滤页路径，如 /sunshine/cafe/all/books?s=Books */
    val path: String = "",
    /** 级别标签：adult / youth / kids / children，普通分类为 null */
    val level: String? = null
) : Parcelable {
    val id: String get() = path
}

/**
 * Cafe 视频条目
 */
@Parcelize
data class CafeVideo(
    /** modal 容器 id（如 8200） */
    val id: String = "",
    val title: String = "",
    val description: String = "",
    val categories: List<CafeCategory> = emptyList(),
    /** 缩略图完整 URL（vidXXXX.jpg） */
    val thumbnailURL: String? = null,
    /** 视频完整 URL（vidXXXX.mp4） */
    val videoURL: String? = null,
    /** 音频完整 URL（当前站点暂无，预留兜底） */
    val audioURL: String? = null,
    /** 所在页面路径（用于区分来自哪个 tab） */
    val levelPath: String = "/sunshine/cafe"
) : Parcelable

/**
 * Cafe 顶部 Tab：固定分级 + 动态搜索/分类页
 * 原版 tab 顺序（<ul id="categorynav">）：All / Adults / Youth / Kids / Children
 */
@Parcelize
data class CafeTab(
    val title: String = "",
    val path: String = ""
) : Parcelable {

    companion object {
        val all = CafeTab("All", "/sunshine/cafe")
        val adults = CafeTab("Adults", "/sunshine/cafe/adults")
        val youth = CafeTab("Youth", "/sunshine/cafe/youth")
        val kids = CafeTab("Kids", "/sunshine/cafe/kids")
        val children = CafeTab("Children", "/sunshine/cafe/children")

        /** 固定分级 Tab */
        val levels: List<CafeTab> = listOf(all, adults, youth, kids, children)

        /** 全文搜索页（/cafe/all/search?s=关键词） */
        fun search(keyword: String): CafeTab {
            val encoded = URLEncoder.encode(keyword, "UTF-8")
            return CafeTab("Search: $keyword", "/sunshine/cafe/all/search?s=$encoded")
        }

        /** 分类过滤页 */
        fun category(category: CafeCategory) = CafeTab(category.name, category.path)

        /** 是否属于固定分级 Tab（动态 Tab 需要展示关闭按钮） */
        fun isLevel(path: String) = levels.any { it.path == path }
    }
}

/** 一次分页结果 */
data class CafePage(
    val videos: List<CafeVideo> = emptyList(),
    val hasNext: Boolean = false
)
