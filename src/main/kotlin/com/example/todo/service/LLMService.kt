package com.example.todo.service

import com.google.gson.JsonParser
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class LLMService(
    private val endpoint: String,
    private val apiKey: String,
    private val model: String
) {
    data class LLMResult(
        val success: Boolean,
        val content: String = "",
        val error: String = ""
    )

    companion object {
        val DEFAULT_PROMPT: String = """
以下是我在项目中的 Git 提交记录，包含不同分支的提交信息：

请用中文总结{periodName}的工作内容：
1. 按分支分类整理，列出每个分支的改动要点
2. 合并同类项，提炼出主要工作方向
3. 语言简洁，使用 Markdown 格式
        """.trimIndent()
    }

    fun summarize(commitText: String, periodName: String = "今天", customPrompt: String? = null): LLMResult {
        if (apiKey.isBlank()) return LLMResult(false, error = "API Key 未配置")
        if (endpoint.isBlank()) return LLMResult(false, error = "API 地址未配置")

        return try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 60000
                readTimeout = 120000
            }

            val body = buildRequestBody(commitText, periodName, customPrompt, stream = false)
            conn.outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            val reader = if (code in 200..299) {
                BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            } else {
                val errorStream = conn.errorStream
                if (errorStream != null) {
                    val err = BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8))
                    return LLMResult(false, error = "HTTP $code: ${err.readText()}")
                } else {
                    return LLMResult(false, error = "HTTP $code: 无错误详情")
                }
            }

            parseResponse(reader.readText())
        } catch (e: java.net.UnknownHostException) {
            LLMResult(false, error = "无法解析域名，请检查 API 地址是否正确")
        } catch (e: java.net.ConnectException) {
            LLMResult(false, error = "连接失败: ${e.message}")
        } catch (e: java.net.SocketTimeoutException) {
            LLMResult(false, error = "请求超时，请检查网络或 API 地址")
        } catch (e: Exception) {
            LLMResult(false, error = "${e.javaClass.simpleName}: ${e.message}")
        }
    }

    fun summarizeStream(
        commitText: String,
        periodName: String = "今天",
        customPrompt: String? = null,
        onChunk: (String) -> Unit,
        onDone: (LLMResult) -> Unit
    ) {
        if (apiKey.isBlank()) { onDone(LLMResult(false, error = "API Key 未配置")); return }
        if (endpoint.isBlank()) { onDone(LLMResult(false, error = "API 地址未配置")); return }

        try {
            val url = URL(endpoint)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
                doOutput = true
                connectTimeout = 60000
                readTimeout = 120000
            }

            val body = buildRequestBody(commitText, periodName, customPrompt, stream = true)
            conn.outputStream.use { os -> os.write(body.toByteArray(Charsets.UTF_8)) }

            val code = conn.responseCode
            if (code !in 200..299) {
                val errorStream = conn.errorStream
                val errMsg = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream, Charsets.UTF_8)).readText()
                } else "无错误详情"
                onDone(LLMResult(false, error = "HTTP $code: $errMsg"))
                return
            }

            val reader = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8))
            val fullContent = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val text = line ?: continue
                if (text.startsWith("data: ")) {
                    val data = text.removePrefix("data: ")
                    if (data == "[DONE]") break
                    val chunk = try {
                        val obj = JsonParser.parseString(data).asJsonObject
                        val choices = obj.getAsJsonArray("choices")
                        choices?.get(0)?.asJsonObject
                            ?.getAsJsonObject("delta")
                            ?.get("content")?.asString ?: ""
                    } catch (_: Exception) { "" }
                    if (chunk.isNotEmpty()) {
                        fullContent.append(chunk)
                        onChunk(chunk)
                    }
                }
            }
            reader.close()

            if (fullContent.isEmpty()) {
                onDone(LLMResult(false, error = "流式响应未返回内容"))
            } else {
                onDone(LLMResult(true, content = fullContent.toString()))
            }
        } catch (e: java.net.UnknownHostException) {
            onDone(LLMResult(false, error = "无法解析域名，请检查 API 地址是否正确"))
        } catch (e: java.net.ConnectException) {
            onDone(LLMResult(false, error = "连接失败: ${e.message}"))
        } catch (e: java.net.SocketTimeoutException) {
            onDone(LLMResult(false, error = "请求超时，请检查网络或 API 地址"))
        } catch (e: Exception) {
            onDone(LLMResult(false, error = "${e.javaClass.simpleName}: ${e.message}"))
        }
    }

    private fun buildRequestBody(commitText: String, periodName: String, customPrompt: String?, stream: Boolean): String {
        val sb = StringBuilder()
        if (!customPrompt.isNullOrBlank()) {
            sb.appendLine(customPrompt.replace("{periodName}", periodName))
            sb.appendLine()
            sb.appendLine(commitText)
        } else {
            sb.appendLine("以下是我在项目中的 Git 提交记录，包含不同分支的提交信息：")
            sb.appendLine()
            sb.appendLine(commitText)
            sb.appendLine()
            sb.appendLine("请用中文总结${periodName}的工作内容：")
            sb.appendLine("1. 按分支分类整理，列出每个分支的改动要点")
            sb.appendLine("2. 合并同类项，提炼出主要工作方向")
            sb.appendLine("3. 语言简洁，使用 Markdown 格式")
        }

        val root = com.google.gson.JsonObject()
        root.addProperty("model", model)
        root.addProperty("temperature", 0.3)
        root.addProperty("stream", stream)

        val messages = com.google.gson.JsonArray()
        val userMsg = com.google.gson.JsonObject()
        userMsg.addProperty("role", "user")
        userMsg.addProperty("content", sb.toString())
        messages.add(userMsg)
        root.add("messages", messages)

        return root.toString()
    }

    private fun parseResponse(json: String): LLMResult {
        return try {
            val obj = JsonParser.parseString(json).asJsonObject
            val choices = obj.getAsJsonArray("choices")
            if (choices != null && choices.size() > 0) {
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                if (message != null) {
                    val content = message.get("content")
                    if (content != null && !content.isJsonNull) {
                        LLMResult(true, content = content.asString)
                    } else {
                        LLMResult(false, error = "API 返回异常：content 字段为空或为 null")
                    }
                } else {
                    LLMResult(false, error = "API 返回异常：未找到 message 字段")
                }
            } else {
                LLMResult(false, error = "API 返回异常：未找到 choices")
            }
        } catch (e: Exception) {
            LLMResult(false, error = "解析响应失败: ${e.javaClass.simpleName}: ${e.message}")
        }
    }
}
