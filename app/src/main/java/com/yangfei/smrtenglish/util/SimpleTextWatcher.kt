package com.yangfei.smrtenglish.util

import android.text.Editable
import android.text.TextWatcher

/**
 * 只关心 afterTextChanged 的简化 TextWatcher
 */
class SimpleTextWatcher(private val onChanged: (String) -> Unit) : TextWatcher {

    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

    override fun afterTextChanged(s: Editable?) = onChanged(s?.toString().orEmpty())
}
