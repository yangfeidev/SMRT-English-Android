package com.yangfei.smrtenglish.util

import android.widget.ImageView
import coil.load
import coil.transform.CircleCropTransformation
import coil.transform.RoundedCornersTransformation
import com.yangfei.smrtenglish.R

/**
 * 图片加载（对齐 iOS Kingfisher 的 KFImage）
 */
fun ImageView.loadUrl(url: String?) {
    load(url) {
        crossfade(true)
        placeholder(R.drawable.bg_card)
        error(R.drawable.bg_card)
    }
}

fun ImageView.loadUrl(url: String?, cornerDp: Float) {
    load(url) {
        crossfade(true)
        placeholder(R.drawable.bg_card)
        error(R.drawable.bg_card)
        transformations(RoundedCornersTransformation(cornerDp))
    }
}

/**
 * 圆形头像：占位/失败时用半透明圆底，露出底下的姓名首字
 */
fun ImageView.loadCircle(url: String?) {
    load(url) {
        crossfade(true)
        placeholder(R.drawable.bg_circle_light)
        error(R.drawable.bg_circle_light)
        transformations(CircleCropTransformation())
    }
}
