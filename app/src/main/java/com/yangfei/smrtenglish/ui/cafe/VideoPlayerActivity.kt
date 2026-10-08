package com.yangfei.smrtenglish.ui.cafe

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.OnBackPressedCallback
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.yangfei.smrtenglish.R
import com.yangfei.smrtenglish.base.BaseActivity
import com.yangfei.smrtenglish.bean.CafeVideo
import com.yangfei.smrtenglish.databinding.ActivityVideoPlayerBinding
import com.yangfei.smrtenglish.util.UiUtils
import com.yangfei.smrtenglish.util.showToast

/**
 * 视频播放页（对齐 iOS VideoPlayerScreen）
 *
 * iOS 需要探测 AV1 编码并回退 HTML5 播放器；Android 端 ExoPlayer 原生支持 AV1，
 * 因此这里直接单播放器，无兜底分支。
 */
class VideoPlayerActivity : BaseActivity<ActivityVideoPlayerBinding, CafeViewModel>() {

    private var player: ExoPlayer? = null
    private var isFullscreen = false

    private val video: CafeVideo by lazy {
        intent.getParcelableExtra(EXTRA_VIDEO) ?: CafeVideo()
    }

    override fun initView(root: View, savedInstanceState: Bundle?) {
        requireTitleBar { it.setTitle(video.title) }

        binding.tvTitle.text = video.title
        binding.tvDesc.isVisible = video.description.isNotEmpty()
        binding.tvDesc.text = video.description

        binding.categoryContainer.removeAllViews()
        video.categories.forEach { category ->
            // 间距 8dp 对齐 iOS VideoPlayerScreen 的 HStack(spacing: 8)
            binding.categoryContainer.addView(
                UiUtils.createCategoryChip(this, category.name, category.level, CATEGORY_GAP_DP)
            )
        }

        binding.btnFullscreen.setOnClickListener { toggleFullscreen() }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (isFullscreen) toggleFullscreen() else finish()
            }
        })
    }

    override fun initObserver() {}

    override fun loadData() {
        val url = video.videoURL ?: video.audioURL
        if (url.isNullOrEmpty()) {
            showToast("该内容暂无可播放地址")
            return
        }
        player = ExoPlayer.Builder(this).build().also { exo ->
            binding.playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    showToast("视频播放失败：${error.errorCodeName}")
                }
            })
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    // MARK: - 全屏

    private fun toggleFullscreen() {
        isFullscreen = !isFullscreen
        binding.topArea.isVisible = !isFullscreen
        binding.detailContainer.isVisible = !isFullscreen

        val lp = binding.playerContainer.layoutParams as LinearLayout.LayoutParams
        val margin16 = UiUtils.dp(this, 16f)
        val marginTop = UiUtils.dp(this, 12f)
        if (isFullscreen) {
            // 播放区在 ScrollView 内，需显式高度撑满可视区
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = binding.scrollDetail.height
            lp.setMargins(0, 0, 0, 0)
            lp.gravity = Gravity.CENTER_HORIZONTAL
        } else {
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.setMargins(margin16, marginTop, margin16, 0)
            lp.gravity = Gravity.NO_GRAVITY
        }
        binding.playerContainer.layoutParams = lp

        if (isFullscreen) {
            // 隐藏标题栏/详情后可视区变高，布局完成后再量一次撑满
            binding.playerContainer.post {
                if (!isFullscreen) return@post
                val lpp = binding.playerContainer.layoutParams as LinearLayout.LayoutParams
                lpp.height = binding.scrollDetail.height
                binding.playerContainer.layoutParams = lpp
            }
        }

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (isFullscreen) {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } else {
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        if (isFullscreen) {
            // 全屏态：旋转后重新撑满可视区
            binding.playerContainer.post {
                if (!isFullscreen) return@post
                val lp = binding.playerContainer.layoutParams as LinearLayout.LayoutParams
                lp.height = binding.scrollDetail.height
                binding.playerContainer.layoutParams = lp
            }
            return
        }
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            // 横屏：16:9 随宽度推算的高度会超出可视区、把标题/简介挤没，
            // 改为按可视高度 62% 定高、宽随 16:9 居中，详情不用滚动即可见
            binding.playerContainer.post {
                if (isFullscreen) return@post
                val viewportH = binding.scrollDetail.height
                if (viewportH <= 0) return@post
                val targetH = (viewportH * LANDSCAPE_PLAYER_VIEWPORT).toInt()
                val targetW = targetH * 16 / 9
                val lp = binding.playerContainer.layoutParams as LinearLayout.LayoutParams
                lp.width = targetW
                lp.height = targetH
                lp.gravity = Gravity.CENTER_HORIZONTAL
                binding.playerContainer.layoutParams = lp
            }
        } else {
            // 竖屏：恢复 16:9 随宽度自适应
            val lp = binding.playerContainer.layoutParams as LinearLayout.LayoutParams
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.gravity = Gravity.NO_GRAVITY
            binding.playerContainer.layoutParams = lp
        }
    }

    override fun onPause() {
        super.onPause()
        player?.pause()
    }

    override fun onDestroy() {
        player?.release()
        player = null
        binding.playerView.player = null
        super.onDestroy()
    }

    companion object {
        const val EXTRA_VIDEO = "extra_video"

        /** 分类标签间距（对齐 iOS VideoPlayerScreen 的 HStack spacing: 8） */
        private const val CATEGORY_GAP_DP = 8f

        /** 横屏非全屏时播放器占可视区高度的比例（留出标题/简介空间） */
        private const val LANDSCAPE_PLAYER_VIEWPORT = 0.62f
    }
}
