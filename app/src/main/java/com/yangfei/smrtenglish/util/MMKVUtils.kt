package com.yangfei.smrtenglish.util

import com.tencent.mmkv.MMKV

/**
 * 本地存储统一入口（对齐参考项目的 MMKVUtils）
 */
object MMKVUtils {

    private val mmkv: MMKV by lazy { MMKV.defaultMMKV() }

    fun putString(key: String, value: String?) {
        if (value == null) mmkv.remove(key) else mmkv.encode(key, value)
    }

    fun getString(key: String, default: String = ""): String = mmkv.decodeString(key, default) ?: default

    fun putInt(key: String, value: Int) {
        mmkv.encode(key, value)
    }

    fun getInt(key: String, default: Int = 0): Int = mmkv.decodeInt(key, default)

    fun putBoolean(key: String, value: Boolean) {
        mmkv.encode(key, value)
    }

    fun getBoolean(key: String, default: Boolean = false): Boolean = mmkv.decodeBool(key, default)

    fun putLong(key: String, value: Long) {
        mmkv.encode(key, value)
    }

    fun getLong(key: String, default: Long = 0L): Long = mmkv.decodeLong(key, default)

    fun remove(key: String) {
        mmkv.remove(key)
    }

    fun contains(key: String): Boolean = mmkv.containsKey(key)
}
