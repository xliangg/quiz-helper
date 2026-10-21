package com.quizhelper.ai

/**
 * 视觉模型预设。自定义时 baseUrl/model 由用户在设置页填写。
 */
data class ProviderPreset(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String
)

object ProviderPresets {
    val DOUBAO = ProviderPreset(
        id = "doubao",
        label = "火山方舟 Doubao（默认）",
        baseUrl = "https://ark.cn-beijing.volces.com/api/v3",
        model = "doubao-seed-2-1-turbo-260628"
    )
    val QWEN = ProviderPreset(
        id = "qwen-vl",
        label = "通义千问 VL",
        baseUrl = "https://dashscope.aliyuncs.com/compatible-mode/v1",
        model = "qwen2.5-vl-72b-instruct"
    )
    val DEEPSEEK = ProviderPreset(
        id = "deepseek-vl",
        label = "DeepSeek-VL（视觉能力有限，仅候选）",
        baseUrl = "https://api.deepseek.com/v1",
        model = "deepseek-chat"
    )
    val CUSTOM = ProviderPreset(
        id = "custom",
        label = "自定义 OpenAI 兼容",
        baseUrl = "",
        model = ""
    )

    val ALL = listOf(DOUBAO, QWEN, DEEPSEEK, CUSTOM)

    fun get(id: String): ProviderPreset? = ALL.firstOrNull { it.id == id }
}
