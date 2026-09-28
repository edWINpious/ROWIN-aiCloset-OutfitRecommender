package com.example.rowin_adlproject.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Minimal Groq chat-completions client (https://api.groq.com - OpenAI schema).
 *
 * Capabilities used:
 *  - [chat]:   text calls (outfit recommendations #21, chatbot #8, alerts)
 *  - [vision]: image + text calls (clothing analysis #6)
 *
 * Models (Groq rotated their catalog in 2026 - llama-4-scout and
 * llama-3.3-70b are deprecated/enterprise-only now; swap here if they rotate again):
 *  - Vision: qwen/qwen3.8-27b        (multimodal, JSON mode - see console.groq.com/docs/vision)
 *  - Text:   openai/gpt-oss-20b      (fast production model)
 */
class GroqClient(
    private val apiKey: String = AiKeys.groqApiKey,
    private val visionModel: String = VISION_MODEL,
    private val textModel: String = TEXT_MODEL,
) {

    /**
     * Sends a text-only prompt and returns the assistant message content.
     * [jsonMode] forces a JSON object response; the chatbot uses free text.
     */
    suspend fun chat(systemPrompt: String, userPrompt: String, jsonMode: Boolean = true): Result<String> =
        request(
            model = textModel,
            body = buildBody(
                model = textModel,
                system = systemPrompt,
                content = userPrompt,
                jsonMode = jsonMode,
            ),
        )

    /** Sends a prompt with a base64 JPEG and returns the assistant message content. */
    suspend fun vision(systemPrompt: String, userPrompt: String, base64Jpeg: String): Result<String> {
        val content = JSONArray()
            .put(JSONObject().put("type", "text").put("text", userPrompt))
            .put(
                JSONObject().put("type", "image_url").put(
                    "image_url",
                    JSONObject().put("url", "data:image/jpeg;base64,$base64Jpeg"),
                ),
            )
        return request(
            model = visionModel,
            body = buildBody(model = visionModel, system = systemPrompt, content = content, jsonMode = true),
        )
    }

    private fun buildBody(model: String, system: String, content: Any, jsonMode: Boolean): JSONObject {
        val messages = JSONArray()
            .put(
                JSONObject().put("role", "system").put("content", system),
            )
            .put(JSONObject().put("role", "user").put("content", content))
        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("temperature", if (jsonMode) 0.4 else 0.8)
            .put("max_completion_tokens", 1024)
            .apply {
                if (jsonMode) put("response_format", JSONObject().put("type", "json_object"))
            }
    }

    private suspend fun request(model: String, body: JSONObject): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val connection = URL(BASE_URL).openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.connectTimeout = 15_000
                connection.readTimeout = 45_000
                connection.doOutput = true
                connection.setRequestProperty("Authorization", "Bearer $apiKey")
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.use { it.write(body.toString().toByteArray()) }

                val code = connection.responseCode
                val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
                connection.disconnect()

                if (code !in 200..299) {
                    error("Groq API error $code: ${text.take(400)}")
                }
                JSONObject(text)
                    .getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content")
            }
        }

    private companion object {
        const val BASE_URL = "https://api.groq.com/openai/v1/chat/completions"

        // TODO: keep model ids in sync with https://console.groq.com/docs/models
        const val VISION_MODEL = "qwen/qwen3.8-27b"
        const val TEXT_MODEL = "openai/gpt-oss-20b"
    }
}
