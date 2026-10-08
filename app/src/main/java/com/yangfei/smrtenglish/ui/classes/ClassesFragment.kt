package com.yangfei.smrtenglish.ui.classes

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.scwang.smart.refresh.header.ClassicsHeader
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseFragment
import com.yangfei.smrtenglish.base.manager.SessionManager
import com.yangfei.smrtenglish.bean.StudentClass
import com.yangfei.smrtenglish.constant.UrlConstant
import com.yangfei.smrtenglish.databinding.FragmentClassesBinding
import com.yangfei.smrtenglish.ui.classes.adapter.ClassAdapter
import com.yangfei.smrtenglish.ui.login.LoginActivity
import com.yangfei.smrtenglish.ui.web.WebActivity
import com.yangfei.smrtenglish.util.SessionEvent

/**
 * 我的班级页（对齐 iOS ClassesView）
 * - 班级卡片：班级名 + 课程 pill + 成员头像列表，点击进班级课程页
 * - Join a Class 卡片：仅无班级时显示，打开官网 Classes 页弹窗（带登录态 WebView）
 * - Me 区块：账号卡 + 退出登录；会话失效时显示「登录已过期」+ 重新登录
 */
class ClassesFragment : BaseFragment<FragmentClassesBinding, ClassesViewModel>() {

    private lateinit var adapter: ClassAdapter

    private var myDisplayName: String? = null

    override fun initView(root: View, savedInstanceState: Bundle?) {
        adapter = ClassAdapter(
            myDisplayNameProvider = { myDisplayName },
            onClassClick = { klass -> openClassCourse(klass) }
        )
        binding.classList.layoutManager = LinearLayoutManager(requireContext())
        binding.classList.adapter = adapter

        binding.swipeRefresh.setRefreshHeader(ClassicsHeader(requireContext()))
        binding.swipeRefresh.setOnRefreshListener { loadClasses(false) }
        binding.btnRetry.setOnClickListener { loadClasses(true) }

        binding.joinCard.setOnClickListener {
            startActivity(Intent(requireContext(), WebActivity::class.java).apply {
                putExtra(WebActivity.EXTRA_TITLE, getString(R.string.classes_join))
                putExtra(WebActivity.EXTRA_URL, UrlConstant.fullUrl(UrlConstant.CLASSES))
            })
        }
        binding.btnLogout.setOnClickListener { confirmLogout() }
        binding.btnRelogin.setOnClickListener { openLoginPage() }

        renderProfile(sessionValid = true)
    }

    override fun initObserver() {
        viewModel.classesResult.observe(viewLifecycleOwner) { response ->
            binding.swipeRefresh.finishRefresh()
            if (response.isSuccessful) {
                val data = response.data ?: ClassesData()
                adapter.submitList(ArrayList(data.classes))
                // 对齐 iOS：仅按 classes 是否为空判断（会话失效时依然显示）
                binding.joinCard.isVisible = data.classes.isEmpty()
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = false
                renderProfile(data.sessionValid)
                if (data.sessionValid) {
                    viewModel.loadDisplayName()
                }
            } else {
                binding.layoutLoading.isVisible = false
                binding.layoutError.isVisible = true
                binding.tvError.text = response.message ?: getString(R.string.error_bad_response)
            }
        }

        viewModel.displayNameResult.observe(viewLifecycleOwner) { response ->
            myDisplayName = response.data?.ifEmpty { null }
            // 显示名到位后刷新一次成员高亮
            adapter.notifyDataSetChanged()
        }
    }

    override fun loadData() {
        loadClasses(true)
    }

    override fun onSessionEvent(event: SessionEvent) {
        if (event.type == SessionEvent.TYPE_RESTORED) {
            loadClasses(false)
        }
    }

    private fun loadClasses(showDialog: Boolean) {
        if (showDialog) {
            binding.layoutLoading.isVisible = true
            binding.layoutError.isVisible = false
        }
        viewModel.loadClasses(showDialog)
    }

    private fun openClassCourse(klass: StudentClass) {
        startActivity(Intent(requireContext(), ClassCourseActivity::class.java).apply {
            putExtra(ClassCourseActivity.EXTRA_CLASS, klass)
        })
    }

    /** Me 区块：会话有效 → 账号卡 + 退出登录；失效 → 「登录已过期」+ 重新登录 */
    private fun renderProfile(sessionValid: Boolean) {
        binding.cardAccount.isVisible = sessionValid
        binding.btnLogout.isVisible = sessionValid
        binding.cardExpired.isVisible = !sessionValid

        if (sessionValid) {
            val email = SessionManager.email ?: getString(R.string.classes_title)
            binding.tvEmail.text = email
            binding.tvAvatar.text = email.take(1).uppercase()
        }
    }

    private fun confirmLogout() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.logout_confirm_title)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.logout_confirm) { _, _ ->
                // 清 cookie 并广播会话失效事件，由 MainActivity 统一切登录页（避免重复起页）
                SessionManager.logout()
            }
            .show()
    }

    private fun openLoginPage() {
        startActivity(
            Intent(requireContext(), LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_FROM_MAIN, true)
        )
    }
}
