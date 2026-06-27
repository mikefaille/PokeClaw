package io.agents.pokeclaw.agent.tools

import com.google.gson.JsonObject

interface ToolArgumentAdapter {
    fun adapt(arguments: JsonObject): JsonObject?
}

data class ToolBinding(
    val externalName: String,
    val internalName: String,
    val argumentAdapter: ToolArgumentAdapter,
    val descriptor: ToolDescriptor
) {
    fun validateAndAdapt(arguments: JsonObject): JsonObject? {
        return argumentAdapter.adapt(arguments)
    }

    suspend fun execute(arguments: JsonObject, context: ToolExecutionContext, internalTool: PokeClawTool): io.agents.pokeclaw.agent.interaction.UnifiedToolResult {
        return internalTool.execute(arguments, context)
    }
}
