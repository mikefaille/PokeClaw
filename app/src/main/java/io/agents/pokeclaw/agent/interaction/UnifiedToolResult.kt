package io.agents.pokeclaw.agent.interaction

import com.google.gson.JsonElement
import com.google.gson.JsonObject

enum class ToolStatus {
    SUCCESS,
    NO_EFFECT,
    REQUIRES_CONFIRMATION,
    BLOCKED,
    INVALID_ARGUMENT,
    UNSUPPORTED,
    CANCELLED,
    FAILED;

    val isTerminal: Boolean
        get() = this == BLOCKED || this == CANCELLED || this == FAILED
}

data class ToolError(
    val code: String,
    val message: String
)

data class EnvironmentSnapshot(
    val screenshotBase64: String?,
    val viewTreeJson: String?
)

data class UnifiedToolResult(
    val callId: String,
    val toolName: String,
    val status: ToolStatus,
    val output: JsonObject,
    val error: ToolError?,
    val environmentChanged: Boolean,
    val snapshot: EnvironmentSnapshot?,
    val safetyAcknowledgement: Boolean,
    val metadata: Map<String, JsonElement>
) {
    companion object {
        fun unsupported(call: UnifiedToolCall): UnifiedToolResult {
            return UnifiedToolResult(
                callId = call.callId,
                toolName = call.name,
                status = ToolStatus.UNSUPPORTED,
                output = JsonObject(),
                error = ToolError("UNSUPPORTED", "Tool ${call.name} is not supported"),
                environmentChanged = false,
                snapshot = null,
                safetyAcknowledgement = false,
                metadata = emptyMap()
            )
        }

        fun invalidArguments(call: UnifiedToolCall): UnifiedToolResult {
            return UnifiedToolResult(
                callId = call.callId,
                toolName = call.name,
                status = ToolStatus.INVALID_ARGUMENT,
                output = JsonObject(),
                error = ToolError("INVALID_ARGUMENT", "Invalid arguments for ${call.name}"),
                environmentChanged = false,
                snapshot = null,
                safetyAcknowledgement = false,
                metadata = emptyMap()
            )
        }

        fun blocked(call: UnifiedToolCall, reason: String): UnifiedToolResult {
            return UnifiedToolResult(
                callId = call.callId,
                toolName = call.name,
                status = ToolStatus.BLOCKED,
                output = JsonObject(),
                error = ToolError("BLOCKED", "Action blocked: $reason"),
                environmentChanged = false,
                snapshot = null,
                safetyAcknowledgement = false,
                metadata = emptyMap()
            )
        }
    }
}
