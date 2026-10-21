package com.quizhelper.core

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * 配置与密钥存储。
 * 密钥仅存于本机 EncryptedSharedPreferences，绝不打印明文到日志。
 */
object Prefs {
    private const val FILE_NAME = "quiz_prefs"
    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        if (::sp.isInitialized) return
        val app = context.applicationContext
        sp = try {
            val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
            EncryptedSharedPreferences.create(
                FILE_NAME,
                masterKeyAlias,
                app,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // 极少数设备 Keystore 异常时回退到普通私有存储，保证可用
            app.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
        }
    }

    var provider: String
        get() = sp.getString(KEY_PROVIDER, "doubao") ?: "doubao"
        set(v) = sp.edit().putString(KEY_PROVIDER, v).apply()

    var apiKey: String
        get() = sp.getString(KEY_API_KEY, "") ?: ""
        set(v) = sp.edit().putString(KEY_API_KEY, v).apply()

    var baseUrl: String
        get() = sp.getString(KEY_BASE_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_BASE_URL, v).apply()

    var modelName: String
        get() = sp.getString(KEY_MODEL, "") ?: ""
        set(v) = sp.edit().putString(KEY_MODEL, v).apply()

    var sysPrompt: String
        get() {
            val v = sp.getString(KEY_SYS_PROMPT, DEFAULT_SYS_PROMPT) ?: DEFAULT_SYS_PROMPT
            // 老版本存过冗长的旧默认提示词，自动升级为更省 token 的新版
            return if (v == LEGACY_SYS_PROMPT) DEFAULT_SYS_PROMPT else v
        }
        set(v) = sp.edit().putString(KEY_SYS_PROMPT, v).apply()

    /** 是否开启深度思考。默认关闭：看图答题不需要推理链，开着会慢一个量级。 */
    var thinkingEnabled: Boolean
        get() = sp.getBoolean(KEY_THINKING, false)
        set(v) = sp.edit().putBoolean(KEY_THINKING, v).apply()

    var maxEdge: Int
        get() = sp.getInt(KEY_MAX_EDGE, 1280)
        set(v) = sp.edit().putInt(KEY_MAX_EDGE, v).apply()

    var jpegQuality: Int
        get() = sp.getInt(KEY_JPEG_QUALITY, 80)
        set(v) = sp.edit().putInt(KEY_JPEG_QUALITY, v).apply()

    var ballEnabled: Boolean
        get() = sp.getBoolean(KEY_BALL_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_BALL_ENABLED, v).apply()

    var overlayGranted: Boolean
        get() = sp.getBoolean(KEY_OVERLAY_GRANTED, false)
        set(v) = sp.edit().putBoolean(KEY_OVERLAY_GRANTED, v).apply()

    var captureGranted: Boolean
        get() = sp.getBoolean(KEY_CAPTURE_GRANTED, false)
        set(v) = sp.edit().putBoolean(KEY_CAPTURE_GRANTED, v).apply()

    fun apiKeyLength(): Int = apiKey.length

    fun clearAll() = sp.edit().clear().apply()

    private const val KEY_PROVIDER = "provider"
    private const val KEY_API_KEY = "api_key"
    private const val KEY_BASE_URL = "base_url"
    private const val KEY_MODEL = "model_name"
    private const val KEY_SYS_PROMPT = "sys_prompt"
    private const val KEY_MAX_EDGE = "max_edge"
    private const val KEY_JPEG_QUALITY = "jpeg_quality"
    private const val KEY_BALL_ENABLED = "ball_enabled"
    private const val KEY_OVERLAY_GRANTED = "overlay_granted"
    private const val KEY_CAPTURE_GRANTED = "capture_granted"
    private const val KEY_THINKING = "thinking_enabled"

    const val DEFAULT_SYS_PROMPT =
        "你是答题助手。用户会发来一张题目截图，请阅读题目与选项，给出最可能的答案，并用简洁中文解析。" +
            "必须且只返回一个 JSON 对象，格式：{\"answer\":\"答案\",\"explanation\":\"解析（不超过80字）\"}。" +
            "answer 只写答案本身（选项字母或填空结果），不要复述题目；解析只讲关键理由。" +
            "不要输出任何额外文字、markdown 代码块或思考过程。"

    /** v1 的默认提示词，用于自动迁移。 */
    private const val LEGACY_SYS_PROMPT =
        "你是答题助手。用户会发来一张题目截图，请阅读图片中的题目与选项，给出最可能的答案，" +
            "并用简洁中文做简要解析。必须且只返回一个 JSON 对象，格式：" +
            "{\"answer\":\"你的答案\",\"explanation\":\"简要解析\"}。" +
            "不要输出任何额外文字、markdown 代码块或解释。"
}
