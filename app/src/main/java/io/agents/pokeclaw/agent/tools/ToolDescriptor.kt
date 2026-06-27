package io.agents.pokeclaw.agent.tools

import com.google.gson.JsonObject

enum class ToolExposure {
    ALWAYS,
    ON_DEMAND,
    INTERNAL
}

enum class ToolRisk {
    LOW,
    MEDIUM,
    HIGH
}

data class ToolDescriptor(
    val internalName: String,
    val description: String,
    val parameters: JsonObject, // Simplified JsonSchema
    val exposure: ToolExposure,
    val risk: ToolRisk,
    val requiresSnapshotBefore: Boolean = false,
    val requiresSnapshotAfter: Boolean = false,
    val requiresEnvironmentSnapshot: Boolean = false, // Kept for compatibility with prompt
    val changesEnvironment: Boolean = false
)
