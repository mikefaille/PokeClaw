package io.agents.pokeclaw.agent.interaction

import com.google.gson.JsonObject

sealed interface ToolOrigin {
    data object ComputerUse : ToolOrigin
    data object PokeClawFunction : ToolOrigin
    data object GoogleBuiltIn : ToolOrigin
}

enum class SafetyDecision {
    Allowed,
    RequiresConfirmation,
    Blocked
}

data class UnifiedToolCall(
    val callId: String,
    val name: String,
    val arguments: JsonObject,
    val origin: ToolOrigin,
    val intent: String?,
    val signature: String?,
    val safetyDecision: SafetyDecision?
)
