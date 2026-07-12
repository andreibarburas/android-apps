package com.brbrs.blik.network

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

data class AiResult(
    val description: String,
    val category: String,
    val tags: List<String>,
)

val BLIK_CATEGORIES = listOf(
    "travel", "food", "lifestyle", "politics", "technology",
    "finance", "health", "entertainment", "sport", "shopping",
    "work", "social", "other"
)

@Singleton
class AiClient @Inject constructor(
    private val okHttpClient: OkHttpClient,
) {
    private val systemPrompt = """
        You are an assistant that analyses screenshots. Respond ONLY with a JSON object, no markdown, no preamble.
        Return exactly this structure:
        {
          "description": "one or two sentence description of what the screenshot shows",
          "category": "one of: ${BLIK_CATEGORIES.joinToString(", ")}",
          "tags": ["tag1", "tag2", "tag3"]
        }
        Tags should be 2-5 lowercase single-word or hyphenated descriptors. Category must be from the provided list.
    """.trimIndent()

    suspend fun analyseWithClaude(imageFile: File, apiKey: String): Result<AiResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
                val payload = JSONObject().apply {
                    put("model", "claude-opus-4-5")
                    put("max_tokens", 512)
                    put("system", systemPrompt)
                    put("messages", JSONArray().put(
                        JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "image")
                                    put("source", JSONObject().apply {
                                        put("type", "base64")
                                        put("media_type", "image/png")
                                        put("data", base64)
                                    })
                                })
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", "Analyse this screenshot.")
                                })
                            })
                        }
                    ))
                }
                val req = Request.Builder()
                    .url("https://api.anthropic.com/v1/messages")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                if (!resp.isSuccessful) throw Exception("Claude API error: HTTP ${resp.code}")
                val body  = JSONObject(resp.body!!.string())
                val text  = body.getJSONArray("content").getJSONObject(0).getString("text").trim()
                parseAiResponse(text)
            }
        }

    suspend fun analyseWithOpenAI(imageFile: File, apiKey: String): Result<AiResult> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base64 = Base64.encodeToString(imageFile.readBytes(), Base64.NO_WRAP)
                val payload = JSONObject().apply {
                    put("model", "gpt-4o")
                    put("max_tokens", 512)
                    put("messages", JSONArray().apply {
                        put(JSONObject().apply {
                            put("role", "system")
                            put("content", systemPrompt)
                        })
                        put(JSONObject().apply {
                            put("role", "user")
                            put("content", JSONArray().apply {
                                put(JSONObject().apply {
                                    put("type", "image_url")
                                    put("image_url", JSONObject().apply {
                                        put("url", "data:image/png;base64,$base64")
                                    })
                                })
                                put(JSONObject().apply {
                                    put("type", "text")
                                    put("text", "Analyse this screenshot.")
                                })
                            })
                        })
                    })
                }
                val req = Request.Builder()
                    .url("https://api.openai.com/v1/chat/completions")
                    .header("Authorization", "Bearer $apiKey")
                    .post(payload.toString().toRequestBody("application/json".toMediaType()))
                    .build()
                val resp = okHttpClient.newCall(req).execute()
                if (!resp.isSuccessful) throw Exception("OpenAI API error: HTTP ${resp.code}")
                val body = JSONObject(resp.body!!.string())
                val text = body.getJSONArray("choices")
                    .getJSONObject(0)
                    .getJSONObject("message")
                    .getString("content").trim()
                parseAiResponse(text)
            }
        }

    private fun parseAiResponse(text: String): AiResult {
        val clean = text.removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
        val json  = JSONObject(clean)
        val tagsArr = json.optJSONArray("tags") ?: JSONArray()
        val tags = (0 until tagsArr.length()).map { tagsArr.getString(it) }
        return AiResult(
            description = json.optString("description", ""),
            category    = json.optString("category", "other").lowercase()
                .let { if (it in BLIK_CATEGORIES) it else "other" },
            tags        = tags,
        )
    }
}
