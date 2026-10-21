package com.quizhelper.ai

import com.google.gson.Gson
import com.google.gson.JsonObject
import org.json.JSONObject

/**
 * 组装 OpenAI 兼容的视觉请求，并解析模型返回。
 *
 * 关键参数：
 * - thinking=disabled：豆包 seed 系列默认会走深度思考，思考链很慢；
 *   看图答题属简单任务，关闭后延迟通常下降一个量级。
 * - stream=true：流式返回，首字更早到，也能在等待时反馈「已接收 N 字」。
 */
object VisionGateway {
    private val gson = Gson()

    fun buildRequestBody(
        model: String,
        dataUri: String,
        prompt: String,
        disableThinking: Boolean = true,
        stream: Boolean = true
    ): String {
        val content = listOf(
            mapOf("type" to "image_url", "image_url" to mapOf("url" to dataUri)),
            mapOf("type" to "text", "text" to prompt)
        )
        val req = mutableMapOf<String, Any>(
            "model" to model,
            "messages" to listOf(
                mapOf("role" to "user", "content" to content)
            ),
            "max_tokens" to 500,
            "temperature" to 0.2,
            "stream" to stream,
            "response_format" to mapOf("type" to "json_object")
        )
        if (disableThinking) {
            req["thinking"] = mapOf("type" to "disabled")
        }
        return gson.toJson(req)
    }

    data class Answer(val answer: String, val explanation: String)

    /** 解析非流式的完整响应体。 */
    fun parseAnswer(body: String): Answer {
        return try {
            val root = gson.fromJson(body, JsonObject::class.java)
            val choices = root.getAsJsonArray("choices")
            val message = choices.get(0).asJsonObject.getAsJsonObject("message")
            val content = message.get("content").asString
            parseContent(content)
        } catch (e: Exception) {
            Answer("", "模型返回无法解析：${e.message}")
        }
    }

    /** 解析流式拼接后的正文（通常就是 JSON 文本）。 */
    fun parseContent(content: String): Answer = extractJson(content)

    private fun extractJson(content: String): Answer {
        var text = content.trim()
        // 去掉可能的 ```json ... ``` 围栏
        if (text.startsWith("```")) {
            text = text.substringAfter("```").substringAfter("\n")
            text = text.substringBeforeLast("```").trim()
        }
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start >= 0 && end > start) {
            text = text.substring(start, end + 1)
        }
        return try {
            val obj = JSONObject(text)
            Answer(
                answer = obj.optString("answer", ""),
                explanation = obj.optString("explanation", "")
            )
        } catch (e: Exception) {
            Answer(text, "")
        }
    }
}
