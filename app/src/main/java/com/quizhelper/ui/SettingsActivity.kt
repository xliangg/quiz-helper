package com.quizhelper.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.quizhelper.R
import com.quizhelper.ai.ProviderPresets
import com.quizhelper.ai.QuizClient
import com.quizhelper.core.Prefs
import com.quizhelper.databinding.ActivitySettingsBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private val scope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupProviderSpinner()
        applyPrefsToUi()

        binding.spinnerProvider.onItemSelectedListener =
            object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: AdapterView<*>?, view: View?, pos: Int, id: Long
                ) = onProviderSelected(pos)

                override fun onNothingSelected(parent: AdapterView<*>?) {}
            }

        binding.btnSave.setOnClickListener { save() }
        binding.btnTest.setOnClickListener { testConnection() }
        binding.btnClear.setOnClickListener { confirmClear() }
        binding.btnEye.setOnClickListener { toggleEye() }
    }

    private fun setupProviderSpinner() {
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item,
            ProviderPresets.ALL.map { it.label }
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter
    }

    private fun applyPrefsToUi() {
        val idx = ProviderPresets.ALL.indexOfFirst { it.id == Prefs.provider }.coerceAtLeast(0)
        binding.spinnerProvider.setSelection(idx)
        binding.etApiKey.setText(Prefs.apiKey)
        binding.etBaseUrl.setText(Prefs.baseUrl)
        binding.etModel.setText(Prefs.modelName)
        binding.etSysPrompt.setText(Prefs.sysPrompt)
        binding.etMaxEdge.setText(Prefs.maxEdge.toString())
        binding.etQuality.setText(Prefs.jpegQuality.toString())
        binding.cbThinking.isChecked = Prefs.thinkingEnabled
        onProviderSelected(idx)
        updateKeyHint()
    }

    private fun onProviderSelected(pos: Int) {
        val preset = ProviderPresets.ALL[pos]
        val isCustom = preset.id == "custom"
        binding.chkCustom.isChecked = isCustom
        binding.layoutCustom.visibility = if (isCustom) View.VISIBLE else View.GONE
        if (!isCustom) {
            if (binding.etBaseUrl.text.isNullOrBlank()) binding.etBaseUrl.setText(preset.baseUrl)
            if (binding.etModel.text.isNullOrBlank()) binding.etModel.setText(preset.model)
        }
    }

    private fun save() {
        val pos = binding.spinnerProvider.selectedItemPosition
        val preset = ProviderPresets.ALL[pos]
        Prefs.provider = preset.id
        Prefs.apiKey = binding.etApiKey.text.toString().trim()
        Prefs.baseUrl = binding.etBaseUrl.text.toString().trim()
        Prefs.modelName = binding.etModel.text.toString().trim()
        Prefs.sysPrompt = binding.etSysPrompt.text.toString().trim()
        Prefs.maxEdge = binding.etMaxEdge.text.toString().toIntOrNull() ?: 1280
        Prefs.jpegQuality =
            binding.etQuality.text.toString().toIntOrNull()?.coerceIn(50, 95) ?: 80
        Prefs.thinkingEnabled = binding.cbThinking.isChecked
        updateKeyHint()
        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
    }

    private fun testConnection() {
        val bmp = makeSampleBitmap()
        val dataUri = bitmapToDataUri(bmp, 80)
        binding.tvTestResult.text = "测试中…"
        scope.launch {
            val start = System.currentTimeMillis()
            try {
                val result = withContext(Dispatchers.IO) { QuizClient.solve(dataUri) }
                val seconds = (System.currentTimeMillis() - start) / 1000.0
                binding.tvTestResult.text =
                    "成功（耗时 ${String.format("%.1f", seconds)}s）｜答案：${result.answer}\n解析：${result.explanation}"
            } catch (e: QuizClient.QuizException) {
                val seconds = (System.currentTimeMillis() - start) / 1000.0
                binding.tvTestResult.text = "失败（${String.format("%.1f", seconds)}s）：${e.message}"
            } catch (e: Exception) {
                binding.tvTestResult.text = "失败：${e.message}"
            }
        }
    }

    private fun confirmClear() {
        AlertDialog.Builder(this)
            .setTitle("清除配置")
            .setMessage("将清除 API Key 与所有设置并恢复默认，确定？")
            .setPositiveButton("清除") { _, _ ->
                Prefs.clearAll()
                applyPrefsToUi()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleEye() {
        val isPassword = (binding.etApiKey.inputType
                and android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD) != 0
        binding.etApiKey.inputType = if (isPassword)
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        else
            android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        binding.etApiKey.setSelection(binding.etApiKey.text?.length ?: 0)
    }

    private fun updateKeyHint() {
        val len = Prefs.apiKeyLength()
        binding.tvKeyHint.text = if (len > 0) "已设置（共 $len 位）" else "未设置"
    }

    private fun makeSampleBitmap(): Bitmap {
        val bmp = Bitmap.createBitmap(400, 200, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        c.drawColor(Color.WHITE)
        val p = Paint().apply { color = Color.BLACK; textSize = 28f }
        c.drawText("示例题目：1 + 1 = ?", 20f, 80f, p)
        c.drawText("A.1   B.2   C.3", 20f, 130f, p)
        return bmp
    }

    private fun bitmapToDataUri(bmp: Bitmap, quality: Int): String {
        val out = java.io.ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return "data:image/jpeg;base64," +
            android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP)
    }
}
