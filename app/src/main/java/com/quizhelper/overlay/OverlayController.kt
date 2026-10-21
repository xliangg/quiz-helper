package com.quizhelper.overlay

import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.net.Uri
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import com.quizhelper.R
import com.quizhelper.capture.CaptureCoordinator
import com.quizhelper.databinding.OverlayAnswerCardBinding
import com.quizhelper.databinding.OverlayBallBinding
import com.quizhelper.databinding.OverlayStatusBinding

/**
 * 通过 WindowManager 管理悬浮球与答案卡片。
 * 悬浮球可拖动；点击触发截题；答案卡片只展示与复制，不回填。
 */
object OverlayController {

    private lateinit var context: Context
    private lateinit var wm: WindowManager

    private lateinit var ballBinding: OverlayBallBinding
    private var ballParams: WindowManager.LayoutParams? = null
    private var ballShown = false

    private var cardBinding: OverlayAnswerCardBinding? = null
    private var cardParams: WindowManager.LayoutParams? = null
    private var cardShown = false

    /** 上次拖动后的卡片位置，-1 表示未拖动过（用默认下方位置）。 */
    private var lastCardX = -1
    private var lastCardY = -1

    private var statusBinding: OverlayStatusBinding? = null
    private var statusShown = false

    fun init(ctx: Context) {
        context = ContextThemeWrapper(ctx.applicationContext, R.style.Theme_QuizHelper)
        wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    fun canDrawOverlays(): Boolean = Settings.canDrawOverlays(context)

    fun isBallShown(): Boolean = ballShown

    fun showBall() {
        if (ballShown) return
        if (!canDrawOverlays()) {
            requestOverlayPermission()
            return
        }
        ballBinding = OverlayBallBinding.inflate(LayoutInflater.from(context))
        ballParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 220
        }
        ballBinding.root.setOnTouchListener(BallTouchListener())
        ballBinding.root.setOnClickListener { CaptureCoordinator.requestCapture(context) }
        wm.addView(ballBinding.root, ballParams)
        ballShown = true
    }

    fun hideBall() {
        if (ballShown) {
            try {
                wm.removeView(ballBinding.root)
            } catch (_: Exception) {
            }
            ballShown = false
        }
    }

    fun showAnswerCard(answer: String, explanation: String, footer: String = "") {
        hideStatus()
        dismissCard()
        val binding = OverlayAnswerCardBinding.inflate(LayoutInflater.from(context))
        cardBinding = binding
        binding.tvTitle.text = if (footer.isBlank()) "⠿ 答题结果" else "⠿ 答题结果 · $footer"
        binding.tvAnswer.text = answer.ifBlank { "（未识别到答案）" }
        binding.tvExplanation.text = explanation.ifBlank { "（无解析）" }
        binding.btnCopy.visibility = View.VISIBLE
        binding.btnCopy.setOnClickListener {
            copyText("$answer\n$explanation")
        }
        binding.btnClose.setOnClickListener { dismissCard() }

        val dm = context.resources.displayMetrics
        val cardWidth = (dm.widthPixels * 0.9).toInt()
        cardParams = WindowManager.LayoutParams(
            cardWidth,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            // 默认落在屏幕下半部，避免挡住上方的长题干；拖动过则沿用上次位置
            x = if (lastCardX >= 0) lastCardX else (dm.widthPixels - cardWidth) / 2
            y = if (lastCardY >= 0) lastCardY else (dm.heightPixels * 0.56f).toInt()
        }
        // 标题与答案区都可拖动（不占用按钮和滚动区域）
        val drag = CardDragListener()
        binding.tvTitle.setOnTouchListener(drag)
        binding.tvAnswer.setOnTouchListener(drag)

        wm.addView(binding.root, cardParams)
        cardShown = true
        // 首次布局后把卡片夹进屏幕内，避免超出底部
        binding.root.post {
            val params = cardParams ?: return@post
            if (!cardShown) return@post
            val h = binding.root.height
            if (h > 0) {
                params.x = params.x.coerceIn(0, (dm.widthPixels - binding.root.width).coerceAtLeast(0))
                params.y = params.y.coerceIn(0, (dm.heightPixels - h - dp(12)).coerceAtLeast(0))
                lastCardX = params.x
                lastCardY = params.y
                try {
                    wm.updateViewLayout(binding.root, params)
                } catch (_: Exception) {
                }
            }
        }
    }

    private fun dp(v: Int): Int = (v * context.resources.displayMetrics.density).toInt()

    /** 按住卡片标题/答案区域拖动，位置会被记住，下次直接出现在同一处。 */
    private class CardDragListener : View.OnTouchListener {
        private var startX = 0
        private var startY = 0
        private var downRawX = 0f
        private var downRawY = 0f

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = OverlayController.cardParams ?: return false
            val root = OverlayController.cardBinding?.root ?: return false
            val dm = OverlayController.context.resources.displayMetrics
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = params.x
                    startY = params.y
                    downRawX = event.rawX
                    downRawY = event.rawY
                    root.alpha = 0.9f
                    return true
                }
                MotionEvent.ACTION_MOVE -> {
                    val maxX = (dm.widthPixels - root.width).coerceAtLeast(0)
                    val maxY = (dm.heightPixels - root.height - 24).coerceAtLeast(0)
                    params.x = (startX + (event.rawX - downRawX).toInt()).coerceIn(0, maxX)
                    params.y = (startY + (event.rawY - downRawY).toInt()).coerceIn(0, maxY)
                    try {
                        OverlayController.wm.updateViewLayout(root, params)
                    } catch (_: Exception) {
                    }
                    return true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    root.alpha = 1f
                    OverlayController.lastCardX = params.x
                    OverlayController.lastCardY = params.y
                    return true
                }
            }
            return false
        }
    }

    /**
     * 顶部居中的小型状态胶囊，用于「已截图 / 已发送 / 查询中」这类轻量反馈。
     * 不可点击、不抢焦点，不遮挡用户操作。
     */
    fun showStatus(text: String) {
        if (!canDrawOverlays()) return
        if (statusShown) {
            updateStatus(text)
            return
        }
        val binding = OverlayStatusBinding.inflate(LayoutInflater.from(context))
        binding.tvStatus.text = text
        binding.tvStatus.maxWidth = (context.resources.displayMetrics.widthPixels * 0.78).toInt()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }
        try {
            wm.addView(binding.root, params)
            statusBinding = binding
            statusShown = true
        } catch (_: Exception) {
        }
    }

    /** 更新状态胶囊文案（如计时）。 */
    fun updateStatus(text: String) {
        if (!statusShown) {
            showStatus(text)
            return
        }
        statusBinding?.tvStatus?.text = text
    }

    fun hideStatus() {
        if (!statusShown) return
        try {
            statusBinding?.root?.let { wm.removeView(it) }
        } catch (_: Exception) {
        }
        statusShown = false
        statusBinding = null
    }

    fun showError(msg: String) {
        hideStatus()
        showAnswerCard("出错了", msg)
    }

    fun dismissCard() {
        if (cardShown && cardBinding != null) {
            try {
                wm.removeView(cardBinding!!.root)
            } catch (_: Exception) {
            }
            cardShown = false
            cardBinding = null
        }
    }

    fun copyText(text: String) {
        try {
            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            cm.setPrimaryClip(android.content.ClipData.newPlainText("quiz_answer", text))
            Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
        }
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
        Toast.makeText(context, "请授予「显示在其他应用上层」权限", Toast.LENGTH_LONG).show()
    }

    private class BallTouchListener : View.OnTouchListener {
        private var initialX = 0
        private var initialY = 0
        private var initialTouchX = 0f
        private var initialTouchY = 0f
        private var moved = false

        override fun onTouch(v: View, event: MotionEvent): Boolean {
            val params = OverlayController.ballParams ?: return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    moved = false
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    try {
                        OverlayController.wm.updateViewLayout(v, params)
                    } catch (_: Exception) {
                    }
                    if (kotlin.math.abs(event.rawX - initialTouchX) > 8 ||
                        kotlin.math.abs(event.rawY - initialTouchY) > 8
                    ) moved = true
                    return true
                }
                MotionEvent.ACTION_UP -> return moved
            }
            return false
        }
    }
}
