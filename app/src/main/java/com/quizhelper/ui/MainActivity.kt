package com.quizhelper.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.appcompat.app.AppCompatActivity
import com.quizhelper.R
import com.quizhelper.core.Prefs
import com.quizhelper.databinding.ActivityMainBinding
import com.quizhelper.overlay.OverlayController

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnToggleBall.setOnClickListener { toggleBall() }
        binding.btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        updatePermissionUI()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionUI()
        Prefs.overlayGranted = OverlayController.canDrawOverlays()
    }

    private fun updatePermissionUI() {
        val canOverlay = OverlayController.canDrawOverlays()
        binding.tvOverlayStatus.text =
            if (canOverlay) "悬浮窗权限：已授权" else "悬浮窗权限：未授权（点此开启）"
        binding.tvOverlayStatus.setOnClickListener {
            if (!canOverlay) openOverlaySettings()
        }
        binding.tvCaptureStatus.text =
            if (Prefs.captureGranted) "截屏权限：已授权" else "截屏权限：启动悬浮球后首次点球时授权"

        binding.btnToggleBall.setText(
            if (OverlayController.isBallShown()) R.string.stop_ball else R.string.start_ball
        )
    }

    private fun toggleBall() {
        if (!OverlayController.canDrawOverlays()) {
            openOverlaySettings()
            return
        }
        if (OverlayController.isBallShown()) {
            OverlayController.hideBall()
            binding.btnToggleBall.setText(R.string.start_ball)
        } else {
            OverlayController.showBall()
            binding.btnToggleBall.setText(R.string.stop_ball)
        }
    }

    private fun openOverlaySettings() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}
