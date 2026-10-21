package com.quizhelper.ai

import android.util.Log
import com.quizhelper.core.Prefs
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * 答题客户端：读取配置 -> 组装请求 -> 调用视觉网关 -> 解析答案。
 *
 * 速度优化：
 * 1) 关闭深度思考（thinking=disabled），这是 100 秒级延迟的主因；
 * 2) 流式输出，首字更早到达，等待期间能反馈「已接收 N 字」；
 * 3) max_tokens 收敛到 500，提示词要求解析不超过 80 字。
 */
object QuizClient {
    private const val TAG = "QuizClient"

    class QuizException(val kind: Kind, message: String) : Exception(message) {
        enum class Kind { NO_KEY, CONFIG, HTTP, NETWORK, TIMEOUT, PARSE }
    }

    data class Result(val answer: String, val explanation: String)

    fun solve(dataUri: String, onProgress: (Int) -> Unit = {}): Result {
        val key = Prefs.apiKey
        if (key.isBlank()) {
            throw QuizException(QuizException.Kind.NO_KEY, "未配置 API Key，请到设置页填写")
        }
        val preset = ProviderPresets.get(Prefs.provider)
        val base = Prefs.baseUrl.ifBlank { preset?.baseUrl ?: "" }
        val model = Prefs.modelName.ifBlank { preset?.model ?: "" }
        if (base.isBlank() || model.isBlank()) {
            throw QuizException(QuizException.Kind.CONFIG, "模型或接口地址未配置，请到设置页检查")
        }

        val url = "$base/chat/completions"
        val prompt = Prefs.sysPrompt
        val disableThinking = !Prefs.thinkingEnabled
        val headers = mapOf(
            "Content-Type" to "application/json",
            "Authorization" to "Bearer $key"
        )

        val start = System.currentTimeMillis()
        Log.d(
            TAG,
            "request model=$model, image=${dataUri.length / 1024}KB, thinking=${!disableThinking}, stream=true"
        )

        var content: String
        try {
            content = requestStream(url, headers, model, dataUri, prompt, disableThinking, onProgress)
            if (content.isBlank()) {
                // 服务端未按流式返回（例如忽略 stream），退回非流式再取一次
                Log.d(TAG, "empty stream, fallback to non-stream")
                val body = VisionGateway.buildRequestBody(model, dataUri, prompt, disableThinking, false)
                val full = HttpJson.post(url, headers, body)
                val parsed = VisionGateway.parseAnswer(full)
                Log.d(TAG, "non-stream done in ${System.currentTimeMillis() - start}ms")
                return finish(parsed)
            }
        } catch (e: HttpJson.ApiException) {
            // 个别模型不支持 thinking 字段：去掉再试一次
            if (e.code == 400 && disableThinking && e.body.contains("thinking", true)) {
                Log.w(TAG, "model rejects thinking param, retry without it")
                content = try {
                    requestStream(url, headers, model, dataUri, prompt, false, onProgress)
                } catch (e2: HttpJson.ApiException) {
                    throw mapApi(e2)
                } catch (e2: Exception) {
                    throw mapNetwork(e2)
                }
            } else {
                throw mapApi(e)
            }
        } catch (e: Exception) {
            throw mapNetwork(e)
        }

        Log.d(TAG, "response in ${System.currentTimeMillis() - start}ms, ${content.length} chars")
        return finish(VisionGateway.parseContent(content))
    }

    private fun requestStream(
        url: String,
        headers: Map<String, String>,
        model: String,
        dataUri: String,
        prompt: String,
        disableThinking: Boolean,
        onProgress: (Int) -> Unit
    ): String {
        val body = VisionGateway.buildRequestBody(model, dataUri, prompt, disableThinking, true)
        return HttpJson.postStream(url, headers, body, onProgress)
    }

    private fun finish(answer: VisionGateway.Answer): Result {
        if (answer.answer.isBlank() && answer.explanation.isBlank()) {
            throw QuizException(QuizException.Kind.PARSE, "模型未返回有效答案，请检查提示词或模型设置")
        }
        return Result(answer.answer, answer.explanation)
    }

    private fun mapApi(e: HttpJson.ApiException): QuizException {
        val msg = when (e.code) {
            401, 403 -> "API Key 错误或无权限（HTTP ${e.code}）"
            404 -> "模型名或接口地址错误（HTTP ${e.code}）"
            413 -> "图片过大被服务器拒绝（HTTP 413），请在设置里调低截图边长"
            429 -> "请求过于频繁或被限流（HTTP ${e.code}）"
            else -> "请求失败（HTTP ${e.code}）：${e.body.take(120)}"
        }
        return QuizException(QuizException.Kind.HTTP, msg)
    }

    private fun mapNetwork(e: Exception): QuizException {
        return when (e) {
            is SocketTimeoutException, is InterruptedIOException ->
                QuizException(QuizException.Kind.TIMEOUT, "请求超时：模型 120 秒内未返回，请稍后重试或调低截图边长")
            is UnknownHostException ->
                QuizException(QuizException.Kind.NETWORK, "网络不可用：无法解析服务器地址，请检查网络")
            is ConnectException ->
                QuizException(QuizException.Kind.NETWORK, "无法连接服务器，请检查网络或接口地址")
            else ->
                QuizException(QuizException.Kind.NETWORK, "网络错误：${e.message}")
        }
    }
}
