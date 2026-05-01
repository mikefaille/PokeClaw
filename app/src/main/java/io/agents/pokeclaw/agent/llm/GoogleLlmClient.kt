// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.AiMessage
import dev.langchain4j.data.message.ChatMessage
import dev.langchain4j.data.message.SystemMessage
import dev.langchain4j.data.message.ToolExecutionResultMessage
import dev.langchain4j.data.message.UserMessage
import dev.langchain4j.model.output.TokenUsage
import dev.langchain4j.agent.tool.ToolExecutionRequest
import io.agents.pokeclaw.agent.AgentConfig
import io.agents.pokeclaw.agent.langchain.http.OkHttpClientBuilderAdapter
import io.agents.pokeclaw.utils.XLog
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSource
import java.io.IOException

class GoogleLlmClient(
    private val config: AgentConfig,
    httpClientBuilder: OkHttpClientBuilderAdapter
) : LlmClient {

    private val gson: Gson = GsonBuilder().create()
    private val httpClient: OkHttpClient = httpClientBuilder.buildOkHttpClient()
    private val mediaType = "application/json; charset=utf-8".toMediaType()

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        val requestJson = buildRequestBody(messages, toolSpecs)
        val url = buildUrl(streaming = false)

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(mediaType))
            .build()

        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                val errorBody = response.body?.string() ?: ""
                throw RuntimeException("Google API error: ${response.code} $errorBody")
            }

            val bodyStr = response.body?.string() ?: throw RuntimeException("Empty response body")
            return parseResponse(bodyStr)
        }
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener
    ): LlmResponse {
        val requestJson = buildRequestBody(messages, toolSpecs)
        val url = buildUrl(streaming = true)

        val request = Request.Builder()
            .url(url)
            .post(requestJson.toString().toRequestBody(mediaType))
            .build()

        var finalResponse: LlmResponse? = null
        try {
            httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val errorBody = response.body?.string() ?: ""
                    throw RuntimeException("Google API streaming error: ${response.code} $errorBody")
                }

                val source: BufferedSource = response.body?.source() ?: throw RuntimeException("Empty streaming body")

                var accumulatedText = ""
                var tokenUsage: TokenUsage? = null
                val toolExecutionRequests = mutableListOf<ToolExecutionRequest>()

                while (!source.exhausted()) {
                    val line = source.readUtf8Line() ?: continue
                    if (line.startsWith("data: ")) {
                        val data = line.substring(6).trim()
                        if (data == "[DONE]") continue
                        if (data.isEmpty()) continue

                        try {
                            val chunk = gson.fromJson(data, JsonObject::class.java)
                            val candidates = chunk.getAsJsonArray("candidates")
                            if (candidates != null && candidates.size() > 0) {
                                val candidate = candidates[0].asJsonObject
                                val content = candidate.getAsJsonObject("content")
                                if (content != null) {
                                    val parts = content.getAsJsonArray("parts")
                                    if (parts != null) {
                                        for (i in 0 until parts.size()) {
                                            val part = parts[i].asJsonObject
                                            if (part.has("text")) {
                                                val textToken = part.get("text").asString
                                                accumulatedText += textToken
                                                listener.onPartialText(textToken)
                                            } else if (part.has("functionCall")) {
                                                val funcCall = part.getAsJsonObject("functionCall")
                                                val name = funcCall.get("name").asString
                                                val args = if (funcCall.has("args")) gson.toJson(funcCall.getAsJsonObject("args")) else "{}"
                                                toolExecutionRequests.add(ToolExecutionRequest.builder()
                                                    .id("call_" + System.currentTimeMillis() + "_" + i)
                                                    .name(name)
                                                    .arguments(args)
                                                    .build())
                                            }
                                        }
                                    }
                                }
                            }

                            val usageMetadata = chunk.getAsJsonObject("usageMetadata")
                            if (usageMetadata != null) {
                                val promptTokens = if (usageMetadata.has("promptTokenCount")) usageMetadata.get("promptTokenCount").asInt else 0
                                val completionTokens = if (usageMetadata.has("candidatesTokenCount")) usageMetadata.get("candidatesTokenCount").asInt else 0
                                val totalTokens = if (usageMetadata.has("totalTokenCount")) usageMetadata.get("totalTokenCount").asInt else 0
                                tokenUsage = TokenUsage(promptTokens, completionTokens, totalTokens)
                            }
                        } catch (e: Exception) {
                            XLog.w("GoogleLlmClient", "Error parsing chunk: $e")
                        }
                    }
                }

                finalResponse = LlmResponse(
                    text = accumulatedText,
                    toolExecutionRequests = toolExecutionRequests,
                    tokenUsage = tokenUsage,
                    modelName = config.modelName
                )
                listener.onComplete(finalResponse!!)
            }
        } catch (e: Exception) {
            listener.onError(e)
            throw e
        }

        return finalResponse ?: LlmResponse("", emptyList(), null, config.modelName)
    }

    private fun buildUrl(streaming: Boolean): String {
        val baseUrl = config.baseUrl.ifEmpty { "https://generativelanguage.googleapis.com/v1beta" }.trimEnd('/')
        val model = config.modelName.ifEmpty { "gemini-3.1-pro-preview" }
        val method = if (streaming) "streamGenerateContent?alt=sse&key=" else "generateContent?key="
        return "$baseUrl/models/$model:$method${config.apiKey}"
    }

    private fun buildRequestBody(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): JsonObject {
        val root = JsonObject()

        // 1. System Prompt
        val systemMessages = messages.filterIsInstance<SystemMessage>()
        if (systemMessages.isNotEmpty()) {
            val sysContent = JsonObject()
            val parts = JsonArray()
            systemMessages.forEach { msg ->
                val part = JsonObject()
                part.addProperty("text", msg.text())
                parts.add(part)
            }
            sysContent.add("parts", parts)
            root.add("systemInstruction", sysContent)
        }

        // 2. Messages
        val contents = JsonArray()
        for (msg in messages) {
            if (msg is SystemMessage) continue

            val content = JsonObject()
            val parts = JsonArray()

            when (msg) {
                is UserMessage -> {
                    content.addProperty("role", "user")
                    val part = JsonObject()
                    part.addProperty("text", msg.singleText())
                    parts.add(part)
                }
                is AiMessage -> {
                    content.addProperty("role", "model")
                    if (msg.text() != null && msg.text().isNotEmpty()) {
                        val part = JsonObject()
                        part.addProperty("text", msg.text())
                        parts.add(part)
                    }
                    if (msg.toolExecutionRequests() != null) {
                        for (req in msg.toolExecutionRequests()) {
                            val part = JsonObject()
                            val funcCall = JsonObject()
                            funcCall.addProperty("name", req.name())
                            val argsObj = try {
                                gson.fromJson(req.arguments(), JsonObject::class.java)
                            } catch (e: Exception) {
                                JsonObject()
                            }
                            funcCall.add("args", argsObj)
                            part.add("functionCall", funcCall)
                            parts.add(part)
                        }
                    }
                }
                is ToolExecutionResultMessage -> {
                    content.addProperty("role", "function")
                    val part = JsonObject()
                    val funcResp = JsonObject()
                    funcResp.addProperty("name", msg.toolName())
                    val responseBody = JsonObject()
                    responseBody.addProperty("result", msg.text())
                    funcResp.add("response", responseBody)
                    part.add("functionResponse", funcResp)
                    parts.add(part)
                }
            }
            content.add("parts", parts)
            contents.add(content)
        }
        root.add("contents", contents)

        // 3. Tools
        if (toolSpecs.isNotEmpty()) {
            val toolsArray = JsonArray()
            val toolObj = JsonObject()
            val functionDeclarations = JsonArray()

            for (spec in toolSpecs) {
                val fd = JsonObject()
                fd.addProperty("name", spec.name())
                if (spec.description() != null) {
                    fd.addProperty("description", spec.description())
                }

                if (spec.parameters() != null) {
                    val paramsObj = spec.parameters()
                    // Map LangChain4j JsonObjectSchema to Gemini Schema
                    val schemaObj = JsonObject()
                    schemaObj.addProperty("type", "OBJECT")

                    val propertiesMap = paramsObj.properties()
                    if (propertiesMap != null && propertiesMap.isNotEmpty()) {
                        val propsObj = JsonObject()
                        for ((key, propSchema) in propertiesMap) {
                            val propJson = JsonObject()
                            val typeName = propSchema.javaClass.simpleName
                            // Simple mapping based on class name
                            val geminiType = when {
                                typeName.contains("String") -> "STRING"
                                typeName.contains("Integer") -> "INTEGER"
                                typeName.contains("Number") -> "NUMBER"
                                typeName.contains("Boolean") -> "BOOLEAN"
                                typeName.contains("Array") -> "ARRAY"
                                typeName.contains("Object") -> "OBJECT"
                                else -> "STRING"
                            }
                            propJson.addProperty("type", geminiType)

                            // Note: Getting description depends on the subclass in Langchain4j 1.12.
                            // We can use reflection or just toString() check.
                            val description = try {
                                val descMethod = propSchema.javaClass.getMethod("description")
                                descMethod.invoke(propSchema) as? String
                            } catch (e: Exception) { null }

                            if (description != null) {
                                propJson.addProperty("description", description)
                            }
                            propsObj.add(key, propJson)
                        }
                        schemaObj.add("properties", propsObj)
                    }

                    val requiredList = paramsObj.required()
                    if (requiredList != null && requiredList.isNotEmpty()) {
                        val reqArr = JsonArray()
                        requiredList.forEach { reqArr.add(it) }
                        schemaObj.add("required", reqArr)
                    }

                    fd.add("parameters", schemaObj)
                }

                functionDeclarations.add(fd)
            }
            toolObj.add("functionDeclarations", functionDeclarations)
            toolsArray.add(toolObj)
            root.add("tools", toolsArray)
        }

        // 4. Generation Config & Thinking Budget
        val generationConfig = JsonObject()
        generationConfig.addProperty("temperature", config.temperature)

        if (config.thinkingBudget != null) {
            val thinkingConfig = JsonObject()
            thinkingConfig.addProperty("thinkingBudget", config.thinkingBudget)
            generationConfig.add("thinkingConfig", thinkingConfig)
        }

        root.add("generationConfig", generationConfig)

        return root
    }

    private fun parseResponse(bodyStr: String): LlmResponse {
        val root = gson.fromJson(bodyStr, JsonObject::class.java)
        val candidates = root.getAsJsonArray("candidates")

        var text = ""
        val toolExecutionRequests = mutableListOf<ToolExecutionRequest>()
        var tokenUsage: TokenUsage? = null

        if (candidates != null && candidates.size() > 0) {
            val candidate = candidates[0].asJsonObject
            val content = candidate.getAsJsonObject("content")
            if (content != null) {
                val parts = content.getAsJsonArray("parts")
                if (parts != null) {
                    for (i in 0 until parts.size()) {
                        val part = parts[i].asJsonObject
                        if (part.has("text")) {
                            text += part.get("text").asString
                        } else if (part.has("functionCall")) {
                            val funcCall = part.getAsJsonObject("functionCall")
                            val name = funcCall.get("name").asString
                            val args = if (funcCall.has("args")) gson.toJson(funcCall.getAsJsonObject("args")) else "{}"
                            toolExecutionRequests.add(ToolExecutionRequest.builder()
                                .id("call_" + System.currentTimeMillis() + "_" + i)
                                .name(name)
                                .arguments(args)
                                .build())
                        }
                    }
                }
            }
        }

        val usageMetadata = root.getAsJsonObject("usageMetadata")
        if (usageMetadata != null) {
            val promptTokens = if (usageMetadata.has("promptTokenCount")) usageMetadata.get("promptTokenCount").asInt else 0
            val completionTokens = if (usageMetadata.has("candidatesTokenCount")) usageMetadata.get("candidatesTokenCount").asInt else 0
            val totalTokens = if (usageMetadata.has("totalTokenCount")) usageMetadata.get("totalTokenCount").asInt else 0
            tokenUsage = TokenUsage(promptTokens, completionTokens, totalTokens)
        }

        return LlmResponse(
            text = text,
            toolExecutionRequests = toolExecutionRequests,
            tokenUsage = tokenUsage,
            modelName = config.modelName
        )
    }
}
