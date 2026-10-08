package com.yangfei.smrtenglish.view.recyclerview.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.chad.library.adapter4.BaseQuickAdapter
import java.lang.reflect.Method
import java.lang.reflect.ParameterizedType

/**
 * 基于 ViewBinding 的 RecyclerView 适配器基类（对齐参考项目 BindingBaseAdapter）
 *
 * @param T 数据实体类型
 * @param VB ViewBinding 类型
 * @param data 初始数据列表
 */
abstract class BindingBaseAdapter<T : Any, VB : ViewBinding>(data: MutableList<T>? = null) :
    BaseQuickAdapter<T, BindingBaseAdapter.VBViewHolder<VB>>(data ?: mutableListOf()) {

    class VBViewHolder<VB : ViewBinding>(val vb: VB, view: View) : RecyclerView.ViewHolder(view)

    /** 缓存的 inflate 方法，每个 Adapter 实例初始化时只反射一次 */
    private val cachedInflateMethod: Method by lazy(LazyThreadSafetyMode.NONE) {
        val vbClass = (javaClass.genericSuperclass as ParameterizedType)
            .actualTypeArguments[1] as Class<VB>
        vbClass.getDeclaredMethod(
            "inflate",
            LayoutInflater::class.java,
            ViewGroup::class.java,
            Boolean::class.java
        )
    }

    override fun onCreateViewHolder(
        context: Context,
        parent: ViewGroup,
        viewType: Int
    ): VBViewHolder<VB> {
        val itemBinding = cachedInflateMethod.invoke(
            null,
            LayoutInflater.from(parent.context),
            parent,
            false
        ) as VB
        return VBViewHolder(itemBinding, itemBinding.root)
    }

    override fun onBindViewHolder(holder: VBViewHolder<VB>, position: Int, item: T?) {
        bindingConvert(holder, item)
    }

    abstract fun bindingConvert(holder: VBViewHolder<VB>, item: T?)
}
