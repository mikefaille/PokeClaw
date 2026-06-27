package io.agents.pokeclaw.agent.interaction

import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.PolicyDecision
import io.agents.pokeclaw.agent.policy.UnifiedPolicyGate
import io.agents.pokeclaw.agent.policy.ConfirmationCoordinator
import io.agents.pokeclaw.agent.tools.ToolBinding
import io.agents.pokeclaw.agent.tools.PokeClawTool
import io.agents.pokeclaw.agent.tools.ToolExecutionContext

sealed class TaskResult {
    data class Success(val output: String) : TaskResult()
    data class Failure(val reason: String) : TaskResult()
    data class Blocked(val reason: String) : TaskResult()
    data class Cancelled(val reason: String) : TaskResult()
    data object IterationLimitReached : TaskResult()
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
        val accumulatedTranscript = mutableListOf<InteractionInput>(UserTextInput(task.prompt))

        while (taskRuntime.canContinue()) {
            val exposedTools = toolExposurePolicy.selectTools(
                task = task,
                capabilities = task.capabilities,
                state = taskRuntime.state
            )

            val interaction = geminiClient.createInteraction(
                model = "gemini-3.5-flash",
                previousInteractionId = previousInteractionId,
                input = accumulatedTranscript,
                tools = exposedTools,
                systemInstruction = promptFactory.create(task)
            )

            previousInteractionId = interaction.id

            val steps = stepNormalizer.normalize(interaction.steps)

            val finalOutput = steps.finalModelOutput()
            if (finalOutput != null && steps.toolCalls().isEmpty()) {
                return TaskResult.Success(finalOutput)
            }

            if (finalOutput != null) {
                accumulatedTranscript.add(ModelTextOutput(finalOutput))
            }

            val results = mutableListOf<UnifiedToolResult>()

            for (call in steps.toolCalls()) {
                accumulatedTranscript.add(ModelToolCall(call))

                val result = executeUnifiedCall(
                    call = call,
                    task = task
                )

                results += result
                accumulatedTranscript.add(interactionSerializer.serialize(result))

                // Handle finish explicitly
                if (call.name == "finish" && result.status == ToolStatus.SUCCESS) {
                    val summary = result.output.get("summary")?.asString ?: "Finished successfully"
                    return TaskResult.Success(summary)
                }

                if (result.status.isTerminal) {
                    return when (result.status) {
                        ToolStatus.BLOCKED -> TaskResult.Blocked(result.error?.message ?: "Action blocked")
                        ToolStatus.CANCELLED -> TaskResult.Cancelled("Action cancelled")
                        ToolStatus.FAILED -> TaskResult.Failure(result.error?.message ?: "Action failed")
                        else -> TaskResult.Failure("Terminal error: ${result.status}")
                    }
                }
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
            ?: return UnifiedToolResult.unsupported(call)

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
