package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.*
import io.agents.pokeclaw.agent.tools.*
import kotlinx.coroutines.runBlocking
import java.util.UUID

class UnifiedInteractionSessionTest {

    @Test
    fun testSuccessfulFinishTerminates() = runBlocking {
        // Setup mocks
        val task = UserTask("id", "test prompt", InteractionCapabilities.DEFAULT_GEMINI)
        val runtime = DefaultTaskRuntime(10)
        val policyGate = DefaultUnifiedPolicyGate()
        val registry = object : ToolRegistryInterface {
            override fun resolve(name: String): ToolBinding? {
                if (name == "finish") {
                    return ToolBinding("finish", "finish_internal", object: ToolArgumentAdapter {
                        override fun adapt(arguments: JsonObject) = arguments
                    }, ToolDescriptor("finish_internal", "Finish", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW))
                }
                return null
            }
            override fun getInternalTool(name: String): PokeClawTool? {
                return object : PokeClawTool {
                    override val descriptor = ToolDescriptor("finish_internal", "Finish", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                    override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): UnifiedToolResult {
                        val output = JsonObject().apply { addProperty("summary", "Done!") }
                        return UnifiedToolResult("call1", "finish", ToolStatus.SUCCESS, output, null, false, null, false, emptyMap())
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
                        return listOf(UnifiedToolCall("call1", "finish", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null))
                    }
                }
            }
        }
        val confirmationCoordinator = object : ConfirmationCoordinator {
            override suspend fun confirmAndExecute(call: UnifiedToolCall, binding: ToolBinding, arguments: JsonObject, decision: PolicyDecision.RequiresConfirmation, context: ToolExecutionContext, internalTool: PokeClawTool) = UnifiedToolResult.blocked(call, "No confirmation")
        }

        val session = UnifiedInteractionSession(
            taskRuntime = runtime,
            toolExposurePolicy = object: ToolExposurePolicy { override fun selectTools(task: UserTask, capabilities: InteractionCapabilities, state: TaskState) = emptyList<GeminiToolDeclaration>() },
            geminiClient = geminiClient,
            promptFactory = object: PromptFactory { override fun create(task: UserTask) = "prompt" },
            stepNormalizer = stepNormalizer,
            interactionSerializer = object: InteractionResultSerializer { override fun serialize(result: UnifiedToolResult) = UserTextInput("res") },
            registry = registry,
            policyGate = policyGate,
            confirmationCoordinator = confirmationCoordinator,
            executionContext = object : ToolExecutionContext {}
        )

        val result = session.runInteraction(task)

        assertTrue("Result should be Success", result is TaskResult.Success)
        assertEquals("Done!", (result as TaskResult.Success).output)
    }
}
