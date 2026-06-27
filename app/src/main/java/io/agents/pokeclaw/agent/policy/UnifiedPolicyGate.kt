package io.agents.pokeclaw.agent.policy

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.tools.ToolDescriptor
import io.agents.pokeclaw.agent.interaction.UserTask
import io.agents.pokeclaw.agent.interaction.SafetyDecision
import io.agents.pokeclaw.agent.tools.ToolRisk

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

        // Gemini server side evaluation override
        if (call.safetyDecision == SafetyDecision.Blocked) {
             return PolicyDecision.Blocked("Blocked by model safety decision")
        }

        if (call.safetyDecision == SafetyDecision.RequiresConfirmation) {
            return PolicyDecision.RequiresConfirmation("The model requires confirmation for this action")
        }

        // Local evaluation logic based on tool risk
        if (descriptor.risk == ToolRisk.HIGH) {
            return PolicyDecision.RequiresConfirmation("High risk action requires confirmation: ${descriptor.description}")
        }

        return PolicyDecision.Allowed
    }
}
