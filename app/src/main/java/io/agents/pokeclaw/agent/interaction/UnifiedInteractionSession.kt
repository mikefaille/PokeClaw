package io.agents.pokeclaw.agent.interaction

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.PolicyDecision
import io.agents.pokeclaw.agent.policy.UnifiedPolicyGate
import io.agents.pokeclaw.agent.policy.ConfirmationCoordinator
import io.agents.pokeclaw.agent.tools.ToolBinding
import io.agents.pokeclaw.agent.tools.PokeClawTool
import io.agents.pokeclaw.agent.tools.ToolExecutionContext
import io.agents.pokeclaw.agent.policy.UserTask

// Stubs for remaining dependencies in the loop
interface TaskState
interface TaskRuntime {
    fun canContinue(): Boolean
    val state: TaskState
}
sealed class TaskResult {
    data class Success(val output: String) : TaskResult()
    data object IterationLimitReached : TaskResult()
}

interface InteractionInput

data class UserTextInput(val text: String) : InteractionInput

interface GeminiToolDeclaration
interface ToolExposurePolicy {
    fun selectTools(
        task: UserTask,
        capabilities: InteractionCapabilities,
        state: TaskState
    ): List<GeminiToolDeclaration>
}

interface PromptFactory {
    fun create(task: UserTask): String
}

interface GeminiInteraction {
    val id: String
    val steps: List<Any> // Simplified
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

interface InteractionStepNormalizer  {
    fun normalize(steps: List<Any>): InteractionSteps
}

interface InteractionResultSerializer  {
    fun serialize(result: UnifiedToolResult): InteractionInput
}

interface ToolRegistryInterface {
    fun resolve(name: String): ToolBinding?
    fun getInternalTool(name: String): PokeClawTool?
}

class UnifiedInteractionSession(
    private val taskRuntime: TaskRuntime,
    private val toolExposurePolicy: ToolExposurePolicy,
    private val geminiClient: GeminiClient,
    private val promptFactory: PromptFactory,
    private val stepNormalizer: InteractionStepNormalizer,
    private val interactionSerializer: InteractionResultSerializer,
    private val registry: ToolRegistryInterface,
    private val policyGate: UnifiedPolicyGate,
    private val confirmationCoordinator: ConfirmationCoordinator,
    private val executionContext: ToolExecutionContext
) {

    suspend fun runInteraction(task: UserTask): TaskResult {
        var previousInteractionId: String? = null
        var pendingInput: List<InteractionInput> = listOf(UserTextInput(task.prompt))

        while (taskRuntime.canContinue()) {
            val exposedTools = toolExposurePolicy.selectTools(
                task = task,
                capabilities = task.capabilities,
                state = taskRuntime.state
            )

            val interaction = geminiClient.createInteraction(
                model = "gemini-3.5-flash",
                previousInteractionId = previousInteractionId,
                input = pendingInput,
                tools = exposedTools,
                systemInstruction = promptFactory.create(task)
            )

            previousInteractionId = interaction.id

            val steps = stepNormalizer.normalize(interaction.steps)

            val finalOutput = steps.finalModelOutput()
            if (finalOutput != null && steps.toolCalls().isEmpty()) {
                return TaskResult.Success(finalOutput)
            }

            val results = mutableListOf<UnifiedToolResult>()

            for (call in steps.toolCalls()) {
                val result = executeUnifiedCall(
                    call = call,
                    task = task
                )

                results += result

                if (result.status.isTerminal) {
                    return TaskResult.Success("Terminal status: \${result.status}") // Simplified mapping
                }
            }

            pendingInput = results.map {
                interactionSerializer.serialize(it)
            }
        }

        return TaskResult.IterationLimitReached
    }

    private suspend fun executeUnifiedCall(
        call: UnifiedToolCall,
        task: UserTask
    ): UnifiedToolResult {
        val binding = registry.resolve(call.name)
            ?: return UnifiedToolResult.unsupported(call)

        val internalTool = registry.getInternalTool(binding.internalName)
            ?: return UnifiedToolResult.unsupported(call) // Or generic error

        val validated = binding.validateAndAdapt(call.arguments)
            ?: return UnifiedToolResult.invalidArguments(call)

        val policyDecision = policyGate.evaluate(
            task = task,
            call = call,
            descriptor = binding.descriptor,
            arguments = validated
        )

        return when (policyDecision) {
            is PolicyDecision.Allowed ->
                binding.execute(validated, executionContext, internalTool)

            is PolicyDecision.RequiresConfirmation ->
                confirmationCoordinator.confirmAndExecute(
                    call,
                    binding,
                    validated,
                    policyDecision,
                    executionContext,
                    internalTool
                )

            is PolicyDecision.Blocked ->
                UnifiedToolResult.blocked(call, policyDecision.reason)
        }
    }
}
