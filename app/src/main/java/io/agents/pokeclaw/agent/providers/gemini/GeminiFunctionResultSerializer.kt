package io.agents.pokeclaw.agent.providers.gemini

import io.agents.pokeclaw.agent.interaction.InteractionResultSerializer
import io.agents.pokeclaw.agent.interaction.UnifiedToolResult
import io.agents.pokeclaw.agent.interaction.InteractionInput

// Custom InteractionInput type that holds Gemini Parts
data class GeminiFunctionResultInput(val callId: String, val toolName: String, val output: com.google.gson.JsonObject) : InteractionInput

class GeminiFunctionResultSerializer : InteractionResultSerializer {
    override fun serialize(result: UnifiedToolResult): InteractionInput {
        return GeminiFunctionResultInput(
            callId = result.callId,
            toolName = result.toolName,
            output = result.output
        )
    }
}
