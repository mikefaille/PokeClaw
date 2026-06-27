package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.FunctionCall
import com.google.genai.types.FunctionResponse
import com.google.genai.types.GenerateContentConfig
import io.agents.pokeclaw.agent.interaction.GeminiClient
import io.agents.pokeclaw.agent.interaction.GeminiInteraction
import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration
import io.agents.pokeclaw.agent.interaction.InteractionInput
import io.agents.pokeclaw.agent.interaction.UserTextInput
import io.agents.pokeclaw.agent.interaction.ModelTextOutput
import io.agents.pokeclaw.agent.interaction.ModelToolCall
import java.util.UUID

class GeminiInteractionsClient(private val apiKey: String) : GeminiClient {
    private val client: Client = Client.builder().apiKey(apiKey).build()

    override suspend fun createInteraction(
        model: String,
        previousInteractionId: String?,
        input: List<InteractionInput>,
        tools: List<GeminiToolDeclaration>,
        systemInstruction: String
    ): GeminiInteraction {

        val contents = mutableListOf<Content>()
        var currentRole = "user"
        var currentParts = mutableListOf<Part>()

        fun flushParts() {
            if (currentParts.isNotEmpty()) {
                contents.add(Content.builder().role(currentRole).parts(currentParts.toList()).build())
                currentParts.clear()
            }
        }

        for (item in input) {
            when (item) {
                is UserTextInput -> {
                    if (currentRole != "user") flushParts()
                    currentRole = "user"
                    currentParts.add(Part.builder().text(item.text).build())
                }
                is ModelTextOutput -> {
                    if (currentRole != "model") flushParts()
                    currentRole = "model"
                    currentParts.add(Part.builder().text(item.text).build())
                }
                is ModelToolCall -> {
                    if (currentRole != "model") flushParts()
                    currentRole = "model"
                    val argsMap = com.google.gson.Gson().fromJson(item.call.arguments, Map::class.java) as Map<String, Any>
                    currentParts.add(Part.builder().functionCall(
                        FunctionCall.builder()
                            .name(item.call.name)
                            .args(argsMap)
                            .build()
                    ).build())
                }
                is GeminiFunctionResultInput -> {
                    if (currentRole != "user") flushParts()
                    currentRole = "user"

                    val responseMap = mutableMapOf<String, Any>()
                    responseMap["output"] = com.google.gson.Gson().fromJson(item.output, Map::class.java) as Map<String, Any>? ?: emptyMap<String, Any>()
                    responseMap["status"] = item.status
                    if (item.error != null) {
                         responseMap["error"] = com.google.gson.Gson().fromJson(item.error, Map::class.java) as Map<String, Any>
                    }

                    currentParts.add(Part.builder().functionResponse(
                        FunctionResponse.builder()
                            .name(item.toolName)
                            .response(responseMap)
                            .build()
                    ).build())
                }
            }
        }
        flushParts()

        val configBuilder = GenerateContentConfig.builder()
            .systemInstruction(Content.builder().parts(listOf(Part.builder().text(systemInstruction).build())).build())

        val serializedTools = GeminiToolCatalogSerializer.serialize(tools)
        if (serializedTools.isNotEmpty()) {
            configBuilder.tools(serializedTools)
        }

        val response = client.models.generateContent(model, contents, configBuilder.build())

        return object : GeminiInteraction {
            override val id: String = previousInteractionId ?: UUID.randomUUID().toString()
            override val steps: List<Any> = listOf(response)
        }
    }
}
