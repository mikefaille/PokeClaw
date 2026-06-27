package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.Part
import com.google.genai.types.FunctionResponse
import com.google.genai.types.GenerateContentConfig
import io.agents.pokeclaw.agent.interaction.GeminiClient
import io.agents.pokeclaw.agent.interaction.GeminiInteraction
import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration
import io.agents.pokeclaw.agent.interaction.InteractionInput
import io.agents.pokeclaw.agent.interaction.UserTextInput
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

        val contents = input.map {
            when (it) {
                is UserTextInput -> Content.builder().role("user").parts(listOf(Part.builder().text(it.text).build())).build()
                is GeminiFunctionResultInput -> {
                    // We map the raw JSON Object to a generic map for genai sdk
                    val responseMap = com.google.gson.Gson().fromJson(it.output, Map::class.java) as Map<String, Any>
                    Content.builder().role("user").parts(listOf(
                        Part.builder().functionResponse(
                            FunctionResponse.builder()
                                .name(it.toolName)
                                .response(responseMap)
                                .build()
                        ).build()
                    )).build()
                }
                else -> Content.builder().role("user").parts(listOf(Part.builder().text(it.toString()).build())).build()
            }
        }

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
