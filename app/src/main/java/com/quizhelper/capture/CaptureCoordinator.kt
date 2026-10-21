package com.quizhelper.capture

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.quizhelper.ai.QuizClient
import com.quizhelper.overlay.OverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 串联一次答题流程：点球 -> 隐藏自身 -> 截帧 -> 发视觉模型 -> 显示答案卡片。
 *
 * 全程通过顶部小胶囊反馈：已截图 xxKB -> 正在发送 -> 等待模型返回（Ns）。
 * 截屏期间不能显示任何悬浮窗，否则会被截进画面，所以第一帧提示出现在截图完成之后。
 */
object CaptureCoordinator {

    private val scope = CoroutineScope(Dispatchers.Main)
    private val running = AtomicBoolean(false)

    /** 悬浮球点击入口。 */
    fun requestCapture(context: Context) {
        buzz(context)
        if (ScreenCaptureManager.isReady()) {
            runCapture(context)
        } else {
            val intent = Intent(context, CapturePermissionActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    /** 执行截帧 + AI 作答 + 展示。 */
    fun runCapture(context: Context) {
        if (!running.compareAndSet(false, true)) return
        // 先清掉悬浮窗，避免把自己截进画面
        OverlayController.dismissCard()
        OverlayController.hideStatus()
        OverlayController.hideBall()
        scope.launch {
            var ticker: kotlinx.coroutines.Job? = null
            try {
                val shot = withContext(Dispatchers.IO) {
                    ScreenCaptureManager.captureToDataUri()
                }
                if (shot == null) {
                    OverlayController.showError("当前页面不支持截屏（可能受保护）或截屏失败")
                    return@launch
                }

                val kb = (shot.jpegBytes / 1024).coerceAtLeast(1)
                OverlayController.showStatus("已截图 ${kb}KB（${shot.width}×${shot.height}），正在发送…")

                val started = System.currentTimeMillis()
                val received = java.util.concurrent.atomic.AtomicInteger(0)
                ticker = launch {
                    var seconds = 0
                    while (isActive) {
                        delay(1000)
                        seconds++
                        val chars = received.get()
                        OverlayController.updateStatus(
                            when {
                                chars > 0 -> "模型正在作答（已接收 ${chars} 字 · ${seconds}s）"
                                seconds <= 1 -> "已发送请求，等待模型返回（${seconds}s）"
                                else -> "等待模型返回（${seconds}s）"
                            }
                        )
                    }
                }

                val result = withContext(Dispatchers.IO) {
                    QuizClient.solve(shot.dataUri) { total -> received.set(total) }
                }
                ticker.cancel()
                OverlayController.hideStatus()
                val seconds = (System.currentTimeMillis() - started) / 1000.0
                OverlayController.showAnswerCard(
                    result.answer,
                    result.explanation,
                    String.format("%.1fs", seconds)
                )
            } catch (e: QuizClient.QuizException) {
                OverlayController.showError(e.message ?: "出错了")
            } catch (e: Exception) {
                OverlayController.showError("出错了：${e.message}")
            } finally {
                ticker?.cancel()
                running.set(false)
                OverlayController.showBall()
            }
        }
    }

    /** 点击时给一点触觉反馈，让用户确认点到了。 */
    private fun buzz(context: Context) {
        try {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager =
                    context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                manager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            }
            if (!vibrator.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(
                    VibrationEffect.createOneShot(18, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(18)
            }
        } catch (_: Exception) {
        }
    }
}
