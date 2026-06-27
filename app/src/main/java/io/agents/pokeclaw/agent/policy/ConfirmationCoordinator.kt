package io.agents.pokeclaw.agent.policy

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.interaction.UnifiedToolResult
import io.agents.pokeclaw.agent.tools.ToolBinding
import io.agents.pokeclaw.agent.tools.PokeClawTool
import io.agents.pokeclaw.agent.tools.ToolExecutionContext

interface ConfirmationCoordinator {
    suspend fun confirmAndExecute(
        call: UnifiedToolCall,
        binding: ToolBinding,
        arguments: JsonObject,
        decision: PolicyDecision.RequiresConfirmation,
        context: ToolExecutionContext,
        internalTool: PokeClawTool
    ): UnifiedToolResult
}
