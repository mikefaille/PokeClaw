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
    }

    private val gson: Gson = GsonBuilder().create()

    private val client: Client = Client.builder()
        .apiKey(config.apiKey)
        .build()

    private fun convertMessages(messages: List<ChatMessage>): List<Content> {
        val contents = mutableListOf<Content>()

        for (msg in messages) {
            when (msg) {
                is SystemMessage -> continue
                is UserMessage -> {
                    val parts = mutableListOf<Part>()
                    if (msg.hasSingleText()) {
                        parts.add(Part.builder().text(msg.singleText()).build())
                    }
                    contents.add(Content.builder().role("user").parts(parts).build())
                }
                is AiMessage -> {
                    val parts = mutableListOf<Part>()
                    if (msg.text() != null && msg.text().isNotEmpty()) {
                        parts.add(Part.builder().text(msg.text()).build())
                    }
                    if (msg.toolExecutionRequests() != null) {
                        for (req in msg.toolExecutionRequests()) {
                            val argsMap = try {
                                gson.fromJson(req.arguments(), Map::class.java) as Map<String, Any>
                            } catch (e: Exception) {
                                emptyMap<String, Any>()
                            }
                            parts.add(
                                Part.builder().functionCall(
                                    FunctionCall.builder()
                                        .name(req.name())
                                        .args(argsMap)
                                        .build()
                                ).build()
                            )
                        }
                    }
                    contents.add(Content.builder().role("model").parts(parts).build())
                }
                is ToolExecutionResultMessage -> {
                    val parts = mutableListOf<Part>()
                    parts.add(
                        Part.builder().functionResponse(
                            FunctionResponse.builder()
                                .name(msg.toolName())
                                .response(mapOf("result" to msg.text()))
                                .build()
                        ).build()
                    )
                    contents.add(Content.builder().role("user").parts(parts).build())
                }
            }
        }
        return contents
    }

    private fun convertTools(toolSpecs: List<ToolSpecification>): List<Tool> {
        if (toolSpecs.isEmpty()) return emptyList()

        val functionDeclarations = mutableListOf<FunctionDeclaration>()

        for (spec in toolSpecs) {
            val schemaBuilder = Schema.builder().type("OBJECT")

            val paramsObj = spec.parameters()
            if (paramsObj != null) {
                val propertiesMap = paramsObj.properties()
                if (propertiesMap != null && propertiesMap.isNotEmpty()) {
                    val convertedProperties = mutableMapOf<String, Schema>()
                    for ((key, propSchema) in propertiesMap) {
                        val typeName = propSchema.javaClass.simpleName
                        val geminiType = when {
                            typeName.contains("String") -> "STRING"
                            typeName.contains("Integer") -> "INTEGER"
                            typeName.contains("Number") -> "NUMBER"
                            typeName.contains("Boolean") -> "BOOLEAN"
                            typeName.contains("Array") -> "ARRAY"
                            typeName.contains("Object") -> "OBJECT"
                            else -> "STRING"
                        }

                        val description = try {
                            val descMethod = propSchema.javaClass.getMethod("description")
                            descMethod.invoke(propSchema) as? String
                        } catch (e: Exception) { null }

                        val propBuilder = Schema.builder().type(geminiType)
                        if (description != null) {
                            propBuilder.description(description)
                        }
                        convertedProperties[key] = propBuilder.build()
                    }
                    schemaBuilder.properties(convertedProperties)
                }

                val requiredList = paramsObj.required()
                if (requiredList != null && requiredList.isNotEmpty()) {
                    schemaBuilder.required(requiredList)
                }
            }

            val fdBuilder = FunctionDeclaration.builder()
                .name(spec.name())
                .parameters(schemaBuilder.build())

            if (spec.description() != null) {
                fdBuilder.description(spec.description())
            }

            functionDeclarations.add(fdBuilder.build())
        }

        return listOf(Tool.builder().functionDeclarations(functionDeclarations).build())
    }

    override fun chat(messages: List<ChatMessage>, toolSpecs: List<ToolSpecification>): LlmResponse {
        XLog.i(TAG, "chat: model=${config.modelName}, messages=${messages.size}, tools=${toolSpecs.size}")

        val systemMessages = messages.filterIsInstance<SystemMessage>()
        val configBuilder = GenerateContentConfig.builder()
            .temperature(config.temperature.toFloat())

        if (systemMessages.isNotEmpty()) {
            val sysParts = systemMessages.map { Part.builder().text(it.text()).build() }
            configBuilder.systemInstruction(
                Content.builder().parts(sysParts).build()
            )
        }

        val tools = convertTools(toolSpecs)
        if (tools.isNotEmpty()) {
            configBuilder.tools(tools)
        }

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

        val systemMessages = messages.filterIsInstance<SystemMessage>()
        val configBuilder = GenerateContentConfig.builder()
            .temperature(config.temperature.toFloat())

        if (systemMessages.isNotEmpty()) {
            val sysParts = systemMessages.map { Part.builder().text(it.text()).build() }
            configBuilder.systemInstruction(
                Content.builder().parts(sysParts).build()
            )
        }

        val tools = convertTools(toolSpecs)
        if (tools.isNotEmpty()) {
            configBuilder.tools(tools)
        }

        val contents = convertMessages(messages)
        val stream = client.models.generateContentStream(
            config.modelName,
            contents,
            configBuilder.build()
        )

        var fullText = ""
        val allToolExecutionRequests = mutableListOf<ToolExecutionRequest>()

        val chunkIter = stream.iterator()
        while(chunkIter.hasNext()) {
            val chunk = chunkIter.next()
            val chunkText = chunk.text()
            if (!chunkText.isNullOrEmpty()) {
                fullText += chunkText
                listener.onPartialText(chunkText)
            }

            val candidatesOpt = chunk.candidates()
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
                                    val request = ToolExecutionRequest.builder()
                                        .id("call_" + System.currentTimeMillis())
                                        .name(name)
                                        .arguments(argsJson)
                                        .build()
                                    allToolExecutionRequests.add(request)
                                }
                            }
                        }
                    }
                }
            }
        }

        return LlmResponse(
            text = fullText,
            toolExecutionRequests = allToolExecutionRequests,
            modelName = config.modelName
        )
    }

    private fun parseResponse(response: GenerateContentResponse): LlmResponse {
        var text = response.text() ?: ""
        val toolExecutionRequests = mutableListOf<ToolExecutionRequest>()

        val candidatesOpt = response.candidates()
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
                                val request = ToolExecutionRequest.builder()
                                    .id("call_" + System.currentTimeMillis())
                                    .name(name)
                                    .arguments(argsJson)
                                    .build()
                                toolExecutionRequests.add(request)
                            }
                        }
                    }
                }
            }
        }

        return LlmResponse(
            text = text,
            toolExecutionRequests = toolExecutionRequests,
            modelName = config.modelName
        )
    }
}
