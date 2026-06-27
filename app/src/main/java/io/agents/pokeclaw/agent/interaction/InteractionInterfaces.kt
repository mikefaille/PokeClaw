package io.agents.pokeclaw.agent.interaction

interface GeminiToolDeclaration

interface InteractionInput
data class UserTextInput(val text: String) : InteractionInput

interface PromptFactory {
    fun create(task: UserTask): String
}

interface GeminiInteraction {
    val id: String
    val steps: List<Any>
}

interface GeminiClient {
    suspend fun createInteraction(
        model: String,
        previousInteractionId: String?,
        input: List<InteractionInput>,
        tools: List<GeminiToolDeclaration>,
        systemInstruction: String
    ): GeminiInteraction
}

interface InteractionSteps {
    fun finalModelOutput(): String?
    fun toolCalls(): List<UnifiedToolCall>
}

interface InteractionStepNormalizer {
    fun normalize(steps: List<Any>): InteractionSteps
}

interface InteractionResultSerializer {
    fun serialize(result: UnifiedToolResult): InteractionInput
}

interface ToolExposurePolicy {
    fun selectTools(
        task: UserTask,
        capabilities: InteractionCapabilities,
        state: TaskState
    ): List<GeminiToolDeclaration>
}
