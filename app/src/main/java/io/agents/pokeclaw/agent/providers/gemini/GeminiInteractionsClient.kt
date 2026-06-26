package io.agents.pokeclaw.agent.providers.gemini

import io.agents.pokeclaw.agent.interaction.GeminiClient
import io.agents.pokeclaw.agent.interaction.GeminiInteraction
import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration
import io.agents.pokeclaw.agent.interaction.InteractionInput

class GeminiInteractionsClient : GeminiClient {
    override suspend fun createInteraction(
        model: String,
        previousInteractionId: String?,
        input: List<InteractionInput>,
        tools: List<GeminiToolDeclaration>,
        systemInstruction: String
    ): GeminiInteraction {
        // Stub implementation
        return object : GeminiInteraction {
            override val id: String = "gemini-stub-id"
            override val steps: List<Any> = emptyList()
        }
    }
}
