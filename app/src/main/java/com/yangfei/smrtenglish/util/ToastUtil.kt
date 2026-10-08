package com.yangfei.smrtenglish.util

import android.content.Context
import androidx.annotation.StringRes
import com.hjq.toast.ToastUtils

fun showToast(message: String?) {
    if (message.isNullOrEmpty()) return
    ToastUtils.show(message)
}

fun showToast(@StringRes resId: Int) {
    ToastUtils.show(resId)
}

fun Context?.toast(message: String?) {
    showToast(message)
}
