package io.agents.pokeclaw.agent.providers.gemini

import io.agents.pokeclaw.agent.interaction.InteractionResultSerializer
import io.agents.pokeclaw.agent.interaction.UnifiedToolResult
import io.agents.pokeclaw.agent.interaction.InteractionInput
import com.google.gson.JsonObject

// Custom InteractionInput type that holds Gemini Parts
data class GeminiFunctionResultInput(
    val callId: String,
    val toolName: String,
    val output: JsonObject,
    val status: String,
    val error: JsonObject?,
    val metadata: Map<String, com.google.gson.JsonElement>,
    val snapshot: io.agents.pokeclaw.agent.interaction.EnvironmentSnapshot?
) : InteractionInput

class GeminiFunctionResultSerializer : InteractionResultSerializer {
    override fun serialize(result: UnifiedToolResult): InteractionInput {
        val errorJson = result.error?.let {
            val err = JsonObject()
            err.addProperty("code", it.code)
            err.addProperty("message", it.message)
            err
        }

        return GeminiFunctionResultInput(
            callId = result.callId,
            toolName = result.toolName,
            output = result.output,
            status = result.status.name,
            error = errorJson,
            metadata = result.metadata,
            snapshot = result.snapshot
        )
    }
}
