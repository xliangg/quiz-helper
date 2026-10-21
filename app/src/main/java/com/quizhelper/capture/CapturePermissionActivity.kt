package com.quizhelper.capture

import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.quizhelper.R
import com.quizhelper.overlay.OverlayController

/**
 * 透明 Activity，仅用于触发系统「允许截屏」弹窗。
 * 授权后启动 CaptureService 并自动截题；未授权则提示。
 */
class CapturePermissionActivity : AppCompatActivity() {

    companion object {
        const val REQ_CAPTURE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_capture_permission)
        if (ScreenCaptureManager.isReady()) {
            CaptureCoordinator.runCapture(applicationContext)
            finish()
            return
        }
        val mpm = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        @Suppress("DEPRECATION")
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_CAPTURE) {
            if (resultCode == RESULT_OK && data != null) {
                val svc = Intent(this, CaptureService::class.java).apply {
                    putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                    putExtra(CaptureService.EXTRA_DATA, data)
                    putExtra(CaptureService.EXTRA_AUTO_CAPTURE, true)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    startForegroundService(svc)
                } else {
                    startService(svc)
                }
                finish()
            } else {
                OverlayController.showError("未授权截屏，无法作答")
                finish()
            }
        }
    }
}
