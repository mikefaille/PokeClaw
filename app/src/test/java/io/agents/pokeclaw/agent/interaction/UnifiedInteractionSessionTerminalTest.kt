package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.*
import io.agents.pokeclaw.agent.tools.*
import kotlinx.coroutines.runBlocking
import java.util.UUID

class UnifiedInteractionSessionTerminalTest {

    @Test
    fun testBlockedFailureMapsToTaskResult() = runBlocking {
        val task = UserTask("id", "test", InteractionCapabilities.DEFAULT_GEMINI)
        val registry = object : ToolRegistryInterface {
            override fun resolve(name: String): ToolBinding? {
                return ToolBinding("bad_tool", "bad_tool_internal", object: ToolArgumentAdapter {
                    override fun adapt(arguments: JsonObject) = arguments
                }, ToolDescriptor("bad_tool_internal", "Bad", JsonObject(), ToolExposure.ALWAYS, ToolRisk.HIGH))
            }
            override fun getInternalTool(name: String): PokeClawTool? {
                return object : PokeClawTool {
                    override val descriptor = ToolDescriptor("bad_tool_internal", "Bad", JsonObject(), ToolExposure.ALWAYS, ToolRisk.HIGH)
                    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): UnifiedToolResult {
                        return UnifiedToolResult("c1", "bad_tool", ToolStatus.BLOCKED, JsonObject(), ToolError("403", "No"), false, null, false, emptyMap())
                    }
                }
            }
        }
        val geminiClient = object : GeminiClient {
            override suspend fun createInteraction(model: String, previousInteractionId: String?, input: List<InteractionInput>, tools: List<GeminiToolDeclaration>, systemInstruction: String): GeminiInteraction {
                return object : GeminiInteraction {
                    override val id = "123"
                    override val steps = emptyList<Any>()
                }
            }
        }
        val stepNormalizer = object : InteractionStepNormalizer {
            override fun normalize(steps: List<Any>): InteractionSteps {
                return object : InteractionSteps {
                    override fun finalModelOutput() = null
                    override fun toolCalls(): List<UnifiedToolCall> {
                        return listOf(UnifiedToolCall("c1", "bad_tool", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null))
                    }
                }
            }
        }

        val session = UnifiedInteractionSession(
            taskRuntime = DefaultTaskRuntime(10),
            toolExposurePolicy = object: ToolExposurePolicy { override fun selectTools(task: UserTask, capabilities: InteractionCapabilities, state: TaskState) = emptyList<GeminiToolDeclaration>() },
            geminiClient = geminiClient,
            promptFactory = object: PromptFactory { override fun create(task: UserTask) = "prompt" },
            stepNormalizer = stepNormalizer,
            interactionSerializer = object: InteractionResultSerializer { override fun serialize(result: UnifiedToolResult) = UserTextInput("res") },
            registry = registry,
            policyGate = DefaultUnifiedPolicyGate(), // Will pass to execute and return BLOCKED
            confirmationCoordinator = object : ConfirmationCoordinator {
                override suspend fun confirmAndExecute(call: UnifiedToolCall, binding: ToolBinding, arguments: JsonObject, decision: PolicyDecision.RequiresConfirmation, context: ToolExecutionContext, internalTool: PokeClawTool) = UnifiedToolResult.blocked(call, "No")
            },
            executionContext = object : ToolExecutionContext {}
        )

        val result = session.runInteraction(task)
        assertTrue("Expected Blocked, got $result", result is TaskResult.Blocked)
    }
}
