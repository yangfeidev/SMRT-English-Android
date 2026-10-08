package com.yangfei.smrtenglish.ui.login

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import com.blankj.utilcode.util.KeyboardUtils
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.databinding.ActivityForgotPasswordBinding

/**
 * 忘记密码（对齐 iOS ForgotPasswordView，POST /process.php subforgot=1）
 */
class ForgotPasswordActivity : BaseActivity<ActivityForgotPasswordBinding, LoginViewModel>() {

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar { it.setTitle(getString(R.string.forgot_title)) }
        binding.etEmail.setText(intent.getStringExtra(EXTRA_EMAIL) ?: "")
        binding.btnSubmit.setOnClickListener {
            KeyboardUtils.hideSoftInput(this)
            val email = binding.etEmail.text.toString().trim()
            if (email.isEmpty()) return@setOnClickListener
            binding.tvResult.isVisible = false
            viewModel.forgotPassword(email)
        }
    }

    override fun initObserver() {
        viewModel.forgotResult.observe(this) { response ->
            binding.tvResult.isVisible = true
            if (response.isSuccessful) {
                binding.tvResult.setText(R.string.forgot_success)
                binding.tvResult.setTextColor(getColor(R.color.green))
            } else {
                binding.tvResult.text = response.message ?: getString(R.string.error_bad_response)
                binding.tvResult.setTextColor(getColor(R.color.red))
            }
        }
    }

    override fun loadData() {}

    companion object {
        const val EXTRA_EMAIL = "extra_email"
    }
}
