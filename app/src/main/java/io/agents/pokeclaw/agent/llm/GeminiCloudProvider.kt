// Copyright 2026 PokeClaw (agents.io). All rights reserved.
// Licensed under the Apache License, Version 2.0.

package io.agents.pokeclaw.agent.llm

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dev.langchain4j.agent.tool.ToolExecutionRequest
import dev.langchain4j.agent.tool.ToolSpecification
import dev.langchain4j.data.message.*
import io.agents.pokeclaw.agent.AgentConfig
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.Tool
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.GenerateContentResponse
import io.agents.pokeclaw.utils.XLog
import java.util.Optional

class GeminiCloudProvider(
    private val config: AgentConfig
) : LlmClient {

    companion object {
        private const val TAG = "GeminiCloudProvider"
        private const val DEFAULT_OBJECT_TYPE = "OBJECT"
    }

    private val gson: Gson = GsonBuilder().create()
    private val client: Client = Client.builder()
        .apiKey(config.apiKey)
        .build()

    /**
     * Converts LangChain4j messages to Gemini Content objects.
     * Best Practice: Ensures strict role alternation (user -> model).
     */
    private fun convertMessages(messages: List<ChatMessage>): List<Content> {
        val contents = mutableListOf<Content>()
        var currentRole: String? = null
        var currentParts = mutableListOf<Part>()

        messages.filter { it !is SystemMessage }.forEach { msg ->
            val msgRole = when (msg) {
                is UserMessage, is ToolExecutionResultMessage -> "user"
                is AiMessage -> "model"
                else -> "user"
            }

            // Group consecutive parts of the same role
            if (currentRole != null && currentRole != msgRole) {
                contents.add(Content.builder().role(currentRole).parts(currentParts).build())
                currentParts = mutableListOf()
            }
            currentRole = msgRole

            when (msg) {
                is UserMessage -> {
                    if (msg.hasSingleText()) {
                        currentParts.add(Part.builder().text(msg.singleText()).build())
                    }
                }
                is AiMessage -> {
                    msg.text()?.takeIf { it.isNotEmpty() }?.let {
                        currentParts.add(Part.builder().text(it).build())
                    }
                    msg.toolExecutionRequests()?.forEach { req ->
                        val argsMap = parseArguments(req.arguments())
                        val (callId, ts) = splitId(req.id())

                        val fc = FunctionCall.builder()
                            .name(req.name())
                            .args(argsMap)
                            .apply { callId?.let { id(it) } }
                            .build()

                        currentParts.add(
                            Part.builder()
                                .functionCall(fc)
                                .apply { ts?.let { thoughtSignature(it) } }
                                .build()
                        )
                    }
                }
                is ToolExecutionResultMessage -> {
                    val (callId, _) = splitId(msg.id())
                    val fr = FunctionResponse.builder()
                        .name(msg.toolName())
                        .response(mapOf("result" to msg.text()))
                        .apply { callId?.let { id(it) } }
                        .build()

                    currentParts.add(Part.builder().functionResponse(fr).build())
                }
            }
        }

        currentRole?.let {
            contents.add(Content.builder().role(it).parts(currentParts).build())
        }

        return contents
    }

    /**
     * Best Practice: Avoid reflection where possible.
     * Map LangChain4j parameter types to Gemini Schema types explicitly.
     */
    private fun convertTools(toolSpecs: List<ToolSpecification>): List<Tool> {
        if (toolSpecs.isEmpty()) return emptyList()

        val declarations = toolSpecs.map { spec ->
            val properties = spec.parameters()?.properties()?.mapValues { (_, prop) ->
                // Simplified mapping logic - consider a dedicated Mapper class
                Schema.builder()
                    .type(mapType(prop))
                    .description(getPropertyDescription(prop))
                    .build()
            } ?: emptyMap()

            val schema = Schema.builder()
                .type(DEFAULT_OBJECT_TYPE)
                .properties(properties)
                .apply { spec.parameters()?.required()?.let { required(it) } }
                .build()

            FunctionDeclaration.builder()
                .name(spec.name())
                .description(spec.description() ?: "")
                .parameters(schema)
                .build()
        }

        return listOf(Tool.builder().functionDeclarations(declarations).build())
    }

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        XLog.i(TAG, "chat: model=${config.modelName}, messages=${messages.size}, tools=${toolSpecs.size}")
        val configBuilder = setupGenerateConfig(messages, toolSpecs)
        val contents = convertMessages(messages)

        val response = client.models.generateContent(
            config.modelName,
            contents,
            configBuilder.build()
        )
        return parseResponse(response)
    }

    override fun chatStreaming(
        messages: List<ChatMessage>,
        toolSpecs: List<ToolSpecification>,
        listener: StreamingListener
    ): LlmResponse {
        XLog.i(TAG, "chatStreaming: model=${config.modelName}, messages=${messages.size}, tools=${toolSpecs.size}")

        val configBuilder = setupGenerateConfig(messages, toolSpecs)
        val contents = convertMessages(messages)
        val stream = client.models.generateContentStream(
            config.modelName,
            contents,
            configBuilder.build()
        )

        val fullTextBuilder = StringBuilder()
        val allToolExecutionRequests = mutableListOf<ToolExecutionRequest>()

        val chunkIter = stream.iterator()
        while(chunkIter.hasNext()) {
            val chunk = chunkIter.next()
            val chunkText = chunk.text()
            if (!chunkText.isNullOrEmpty()) {
                fullTextBuilder.append(chunkText)
                listener.onPartialText(chunkText)
            }

            allToolExecutionRequests.addAll(extractFunctionCalls(chunk.candidates()))
        }

        return LlmResponse(
            text = fullTextBuilder.toString(),
            toolExecutionRequests = allToolExecutionRequests,
            modelName = config.modelName
        )
    }

    // Helper: Logic shared between chat and chatStreaming
    private fun setupGenerateConfig(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): GenerateContentConfig.Builder {
        val sysInstruction = messages.filterIsInstance<SystemMessage>()
            .joinToString("\n") { it.text() }
            .takeIf { it.isNotEmpty() }

        return GenerateContentConfig.builder()
            .temperature(config.temperature.toFloat())
            .apply {
                sysInstruction?.let {
                    systemInstruction(Content.builder().parts(listOf(Part.builder().text(it).build())).build())
                }
                convertTools(toolSpecs).takeIf { it.isNotEmpty() }?.let { tools(it) }
            }
    }

    private fun extractFunctionCalls(candidatesOpt: Optional<out List<com.google.genai.types.Candidate>>?): List<ToolExecutionRequest> {
        val requests = mutableListOf<ToolExecutionRequest>()
        if (candidatesOpt != null && candidatesOpt.isPresent()) {
            val candidateList = candidatesOpt.get()
            if (candidateList.isNotEmpty()) {
                val contentOpt = candidateList[0].content()
                if (contentOpt != null && contentOpt.isPresent()) {
                    val content = contentOpt.get()
                    val partsOpt = content.parts()
                    if (partsOpt != null && partsOpt.isPresent()) {
                        for (part in partsOpt.get()) {
                            val funcCallOpt = part.functionCall()
                            if (funcCallOpt != null && funcCallOpt.isPresent()) {
                                val funcCall = funcCallOpt.get()
                                val argsMapOpt = funcCall.args()
                                val argsMap = if (argsMapOpt != null && argsMapOpt.isPresent()) argsMapOpt.get() else null
                                val argsJson = if (argsMap != null) gson.toJson(argsMap) else "{}"
                                val nameOpt = funcCall.name()
                                val name = if (nameOpt != null && nameOpt.isPresent()) nameOpt.get() else ""
                                
                                val tsOpt = part.thoughtSignature()
                                val tsBase64 = if (tsOpt != null && tsOpt.isPresent()) java.util.Base64.getEncoder().encodeToString(tsOpt.get()) else null
                                val callIdOpt = funcCall.id()
                                val callId = if (callIdOpt != null && callIdOpt.isPresent()) callIdOpt.get() else "call_" + System.currentTimeMillis()
                                val compositeId = if (tsBase64 != null) "$callId|$tsBase64" else callId

                                val request = ToolExecutionRequest.builder()
                                    .id(compositeId)
                                    .name(name)
                                    .arguments(argsJson)
                                    .build()
                                requests.add(request)
                            }
                        }
                    }
                }
            }
        }
        return requests
    }

    private fun parseResponse(response: GenerateContentResponse): LlmResponse {
        var text = response.text() ?: ""
        val toolExecutionRequests = mutableListOf<ToolExecutionRequest>()

        toolExecutionRequests.addAll(extractFunctionCalls(response.candidates()))

        return LlmResponse(
            text = text,
            toolExecutionRequests = toolExecutionRequests,
            modelName = config.modelName
        )
    }

    private fun splitId(rawId: String?): Pair<String?, ByteArray?> {
        if (rawId == null) return null to null
        val parts = rawId.split("|", limit = 2)
        val id = parts[0].takeIf { it.isNotEmpty() }
        val ts = parts.getOrNull(1)?.let { java.util.Base64.getDecoder().decode(it) }
        return id to ts
    }

    private fun parseArguments(json: String): Map<String, Any> {
        return try {
            gson.fromJson(json, Map::class.java) as Map<String, Any>
        } catch (e: Exception) {
            emptyMap()
        }
    }

    private fun mapType(prop: Any): String {
        return when (prop.javaClass.simpleName) {
            "JsonStringSchemaProperty" -> "STRING"
            "JsonIntegerSchemaProperty" -> "INTEGER"
            "JsonBooleanSchemaProperty" -> "BOOLEAN"
            "JsonNumberSchemaProperty" -> "NUMBER"
            "JsonArraySchemaProperty" -> "ARRAY"
            else -> "STRING"
        }
    }

    private fun getPropertyDescription(prop: Any): String? {
        return try {
            prop.javaClass.getMethod("description").invoke(prop) as? String
        } catch (e: Exception) { null }
    }
}
