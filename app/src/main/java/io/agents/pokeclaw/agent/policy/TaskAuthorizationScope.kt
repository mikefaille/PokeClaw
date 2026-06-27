package io.agents.pokeclaw.agent.policy

import io.agents.pokeclaw.agent.interaction.InteractionCapabilities

interface TaskAuthorizationScope {
    fun isAuthorized(capabilities: InteractionCapabilities, toolName: String): Boolean
}

class DefaultTaskAuthorizationScope : TaskAuthorizationScope {
    override fun isAuthorized(capabilities: InteractionCapabilities, toolName: String): Boolean {
        // Evaluate based on tool name and capabilities
        return true
    }
}
