package com.yangfei.smrtenglish.ui.main

import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.constant.ValueConstant
import com.yangfei.smrtenglish.databinding.ActivityMainBinding
import com.yangfei.smrtenglish.ui.cafe.CafeFragment
import com.yangfei.smrtenglish.ui.classes.ClassesFragment
import com.yangfei.smrtenglish.ui.library.LibraryFragment
import com.yangfei.smrtenglish.ui.login.LoginActivity
import com.yangfei.smrtenglish.util.SessionEvent
import com.yangfei.smrtenglish.util.WebViewHelper

/**
 * 主界面（对齐 iOS MainTabView）
 *
 * 底部 3 Tab 对齐官网导航：Cafe=房子、Library=学士帽、Classes=三人组。
 * Classes 依赖登录态，切入时校验会话（5 分钟内不重复），失效则跳登录页。
 */
class MainActivity : BaseActivity<ActivityMainBinding, MainViewModel>() {

    private val fragments = HashMap<Int, Fragment>()
    private var lastSessionCheckTime = 0L
    private var isLoginPageShowing = false

    /** 当前 Tab（横竖屏切换会重建 Activity，这里靠 onSaveInstanceState 保留） */
    private var currentTabId = R.id.tab_cafe

    override fun initView(root: View, savedInstanceState: Bundle?) {
        currentTabId = savedInstanceState?.getInt(KEY_TAB) ?: R.id.tab_cafe

        // 登录后预热 WebView 渲染进程：首次进课时详情（WebView 页）不再卡顿
        //（对齐 iOS MainTabView.task 的 WebViewRepresentable.warmUp()）
        WebViewHelper.warmUp(this)

        binding.bottomNav.setOnItemSelectedListener { item ->
            switchTab(item.itemId)
            true
        }
        // 选中态由我们恢复（BottomNavigationView 自身的状态恢复晚于 onCreate）
        binding.bottomNav.selectedItemId = currentTabId
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(KEY_TAB, currentTabId)
        super.onSaveInstanceState(outState)
    }

    override fun initObserver() {
        viewModel.sessionCheckResult.observe(this) { response ->
            if (response.isSuccessful && response.data == false) {
                openLoginPage()
            }
        }
    }

    override fun loadData() {}

    override fun onSessionEvent(event: SessionEvent) {
        if (event.type == SessionEvent.TYPE_INVALID) {
            openLoginPage()
        }
    }

    private fun switchTab(itemId: Int): Boolean {
        currentTabId = itemId
        val tag = tagOf(itemId)
        val fm = supportFragmentManager
        val transaction = fm.beginTransaction()
        var target = fm.findFragmentByTag(tag)

        fm.fragments.forEach { fragment ->
            if (fragment.isAdded && fragment.tag != tag) transaction.hide(fragment)
        }

        if (target == null) {
            target = createFragment(itemId)
            fragments[itemId] = target
            transaction.add(R.id.fragment_container, target, tag)
        } else {
            transaction.show(target)
        }
        transaction.commitAllowingStateLoss()

        // Classes 依赖登录态（PHPSESSID 服务端约 1-2 小时过期）：切入时校验，5 分钟内不重复
        if (itemId == R.id.tab_classes) {
            maybeValidateSession()
        }
        return true
    }

    private fun tagOf(itemId: Int): String = when (itemId) {
        R.id.tab_library -> "tab_library"
        R.id.tab_classes -> "tab_classes"
        else -> "tab_cafe"
    }

    private fun createFragment(itemId: Int): Fragment = when (itemId) {
        R.id.tab_library -> LibraryFragment()
        R.id.tab_classes -> ClassesFragment()
        else -> CafeFragment()
    }

    private fun maybeValidateSession() {
        val now = System.currentTimeMillis()
        if (now - lastSessionCheckTime < ValueConstant.SESSION_CHECK_INTERVAL) return
        lastSessionCheckTime = now
        viewModel.validateSession()
    }

    private fun openLoginPage() {
        if (isLoginPageShowing) return
        isLoginPageShowing = true
        startActivity(
            Intent(this, LoginActivity::class.java)
                .putExtra(LoginActivity.EXTRA_FROM_MAIN, true)
        )
    }

    override fun onResume() {
        super.onResume()
        isLoginPageShowing = false
    }

    companion object {
        private const val KEY_TAB = "main_current_tab"
    }
}
