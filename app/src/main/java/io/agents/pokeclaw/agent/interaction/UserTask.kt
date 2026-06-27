package io.agents.pokeclaw.agent.interaction

// Moved UserTask here to be available globally
data class UserTask(
    val id: String,
    val prompt: String,
    val capabilities: InteractionCapabilities
)
