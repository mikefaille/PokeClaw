package io.agents.pokeclaw.agent.policy

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.tools.ToolDescriptor

// Stub for UserTask
interface UserTask {
    val prompt: String
    val capabilities: io.agents.pokeclaw.agent.interaction.InteractionCapabilities
}

sealed interface PolicyDecision {
    data object Allowed : PolicyDecision
    data class RequiresConfirmation(val message: String) : PolicyDecision
    data class Blocked(val reason: String) : PolicyDecision
}

interface UnifiedPolicyGate {
    fun evaluate(
        task: UserTask,
        call: UnifiedToolCall,
        descriptor: ToolDescriptor,
        arguments: JsonObject
    ): PolicyDecision
}

class DefaultUnifiedPolicyGate : UnifiedPolicyGate {
    override fun evaluate(
        task: UserTask,
        call: UnifiedToolCall,
        descriptor: ToolDescriptor,
        arguments: JsonObject
    ): PolicyDecision {
        return PolicyDecision.Allowed
    }
}
