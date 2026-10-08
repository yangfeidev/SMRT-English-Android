package com.yangfei.smrtenglish.ui.login

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import androidx.core.view.isVisible
import com.blankj.utilcode.util.KeyboardUtils
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.base.manager.SessionManager
import com.yangfei.smrtenglish.databinding.ActivityLoginBinding
import com.yangfei.smrtenglish.ui.main.MainActivity
import com.yangfei.smrtenglish.util.EventBusUtil
import com.yangfei.smrtenglish.util.SessionEvent
import com.yangfei.smrtenglish.util.SimpleTextWatcher

/**
 * 登录页（对齐 iOS LoginView）
 * - 表单：邮箱 + 密码（账密总是记住，下次会话过期后自动回填）
 * - 调 SmrtApi.login（POST /process.php），cookie 由 PersistentCookieJar 持久化
 */
class LoginActivity : BaseActivity<ActivityLoginBinding, LoginViewModel>() {

    private var isEntry = true

    override fun beforeContentView() {
        // 从主界面因会话失效跳转过来时，登录成功后直接返回（不重复创建主界面）
        isEntry = !intent.getBooleanExtra(EXTRA_FROM_MAIN, false)
    }

    override fun initView(root: View, savedInstanceState: Bundle?) {
        // 记住过密码则启动回填
        binding.etEmail.setText(SessionManager.email ?: "")
        binding.etPassword.setText(SessionManager.password ?: "")
        updateLoginButtonState()

        binding.btnLogin.setOnClickListener { doLogin() }
        binding.tvForgot.setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java).apply {
                putExtra(ForgotPasswordActivity.EXTRA_EMAIL, binding.etEmail.text.toString().trim())
            })
        }
        binding.etEmail.addTextChangedListener(SimpleTextWatcher { updateLoginButtonState() })
        binding.etPassword.addTextChangedListener(SimpleTextWatcher { updateLoginButtonState() })
    }

    override fun initObserver() {
        viewModel.loginResult.observe(this) { response ->
            if (response.isSuccessful && response.data == true) {
                binding.tvError.isVisible = false
                EventBusUtil.post(SessionEvent(SessionEvent.TYPE_RESTORED))
                if (isEntry) {
                    startActivity(Intent(this, MainActivity::class.java))
                }
                finish()
            } else {
                binding.tvError.isVisible = true
                binding.tvError.text = response.message
            }
        }
    }

    override fun loadData() {}

    private fun doLogin() {
        // 点登录立即收起键盘，不等登录结果
        KeyboardUtils.hideSoftInput(this)
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        if (email.isEmpty() || password.isEmpty()) return
        binding.tvError.isVisible = false
        viewModel.login(email, password)
    }

    private fun updateLoginButtonState() {
        val enabled = binding.etEmail.text.isNotEmpty() && binding.etPassword.text.isNotEmpty()
        binding.btnLogin.isEnabled = enabled
        binding.btnLogin.alpha = if (enabled) 1f else 0.55f
    }

    companion object {
        const val EXTRA_FROM_MAIN = "extra_from_main"
    }
}
