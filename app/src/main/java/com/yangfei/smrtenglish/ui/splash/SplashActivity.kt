package com.yangfei.smrtenglish.ui.splash

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.yangfei.smrtenglish.databinding.ActivitySplashBinding
import com.yangfei.smrtenglish.http.SmrtApi
import com.yangfei.smrtenglish.ui.login.LoginActivity
import com.yangfei.smrtenglish.ui.main.MainActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 启动页（对齐 iOS RootView 的 checking 阶段）
 *
 * 启动时异步校验 cookie 会话，有效 → 主界面；无效 → 记住过密码则静默重登一次，仍失败则登录页。
 */
class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivitySplashBinding.inflate(layoutInflater).also { setContentView(it.root) }

        lifecycleScope.launch {
            var valid = withContext(Dispatchers.IO) { SmrtApi.checkSession() }
            if (!valid) {
                // cookie 过期：记住过密码则静默重登一次
                valid = withContext(Dispatchers.IO) { SmrtApi.autoReloginIfNeeded() }
            }
            val target = if (valid) MainActivity::class.java else LoginActivity::class.java
            startActivity(Intent(this@SplashActivity, target))
            finish()
        }
    }
}
