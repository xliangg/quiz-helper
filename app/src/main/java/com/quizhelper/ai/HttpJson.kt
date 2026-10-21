package com.quizhelper.ai

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 极简 HTTP JSON 封装。
 *
 * 注意超时设置：视觉模型处理整屏截图 + 生成 JSON 往往需要十几秒到一分钟，
 * 30 秒的读取超时会导致「连接测试成功、点球却超时」的假故障。
 * 这里放宽为：连接 20s / 写入 90s / 读取 120s / 整次调用 150s。
 */
object HttpJson {
    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(90, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .callTimeout(150, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private val JSON = "application/json; charset=utf-8".toMediaType()

    class ApiException(val code: Int, val body: String) :
        Exception("HTTP $code: ${body.take(200)}")

    fun post(url: String, headers: Map<String, String>, jsonBody: String): String {
        val request = buildRequest(url, headers, jsonBody)
        client.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw ApiException(response.code, body)
            return body
        }
    }

    /**
     * 流式请求（SSE）。逐块回调已累计的正文字数，返回完整正文。
     * 服务端若不支持流式而直接返回整包 JSON，也能兜底解析（见 [QuizClient]）。
     */
    fun postStream(
        url: String,
        headers: Map<String, String>,
        jsonBody: String,
        onProgress: (Int) -> Unit = {}
    ): String {
        val request = buildRequest(url, headers, jsonBody)
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw ApiException(response.code, response.body?.string().orEmpty())
            }
            val source = response.body?.source() ?: throw IOException("响应为空")
            val sb = StringBuilder()
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (line.isEmpty() || !line.startsWith("data:")) continue
                val payload = line.removePrefix("data:").trim()
                if (payload == "[DONE]") break
                val piece = extractDelta(payload) ?: continue
                if (piece.isNotEmpty()) {
                    sb.append(piece)
                    onProgress(sb.length)
                }
            }
            return sb.toString()
        }
    }

    /** 从一条 SSE data 中取出增量正文；解析失败返回 null。 */
    private fun extractDelta(payload: String): String? {
        return try {
            val obj = JSONObject(payload)
            val choices = obj.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null
            val delta = choices.getJSONObject(0).optJSONObject("delta") ?: return null
            delta.optString("content", "")
        } catch (_: Exception) {
            null
        }
    }

    private fun buildRequest(
        url: String,
        headers: Map<String, String>,
        jsonBody: String
    ): Request {
        return Request.Builder()
            .url(url)
            .post(jsonBody.toRequestBody(JSON))
            .apply { headers.forEach { (k, v) -> addHeader(k, v) } }
            .build()
    }
}
