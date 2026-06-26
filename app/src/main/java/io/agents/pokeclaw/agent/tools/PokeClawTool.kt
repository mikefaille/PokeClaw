package io.agents.pokeclaw.agent.tools

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.interaction.UnifiedToolResult

interface ToolExecutionContext {
    // Context needed for execution, e.g. Android Context, current task details
}

interface PokeClawTool {
    val descriptor: ToolDescriptor

    suspend fun execute(
        arguments: JsonObject,
        context: ToolExecutionContext
    ): UnifiedToolResult
}
