package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.*
import io.agents.pokeclaw.agent.tools.*
import kotlinx.coroutines.runBlocking

class UnifiedInteractionSessionContextTest {

    @Test
    fun testConversationHistoryPreserved() = runBlocking {
        val task = UserTask("id", "initial prompt", InteractionCapabilities.DEFAULT_GEMINI)
        val transcriptTracker = mutableListOf<List<InteractionInput>>()

        val geminiClient = object : GeminiClient {
            var calls = 0
            override suspend fun createInteraction(model: String, input: List<InteractionInput>, tools: List<GeminiToolDeclaration>, systemInstruction: String): GeminiInteraction {
                calls++
                transcriptTracker.add(input.toList())
                return object : GeminiInteraction {
                    override val id = "i\$calls"
                    override val steps = emptyList<Any>()
                }
            }
        }

        val stepNormalizer = object : InteractionStepNormalizer {
            var calls = 0
            override fun normalize(steps: List<Any>): InteractionSteps {
                calls++
                return object : InteractionSteps {
                    override fun finalModelOutput(): String? {
                        return if (calls == 2) "Final" else "Intermediate text"
                    }
                    override fun toolCalls(): List<UnifiedToolCall> {
                        return if (calls == 1) {
                            listOf(UnifiedToolCall("call1", "test_tool", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null))
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        }

        val session = UnifiedInteractionSession(
            taskRuntime = DefaultTaskRuntime(3),
            toolExposurePolicy = object: ToolExposurePolicy { override fun selectTools(t: UserTask, c: InteractionCapabilities, s: TaskState) = emptyList<GeminiToolDeclaration>() },
            geminiClient = geminiClient,
            promptFactory = object: PromptFactory { override fun create(t: UserTask) = "prompt" },
            stepNormalizer = stepNormalizer,
            interactionSerializer = object: InteractionResultSerializer {
                override fun serialize(r: UnifiedToolResult): InteractionInput {
                    return object : InteractionInput {} // dummy
                }
            },
            registry = object : io.agents.pokeclaw.agent.tools.UnifiedToolRegistry {
                override fun resolve(name: String) = ToolBinding(name, name, object: ToolArgumentAdapter { override fun adapt(a: JsonObject) = a }, ToolDescriptor(name, "", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW))
                override fun getInternalTool(name: String) = object : PokeClawTool {
                    override val descriptor = ToolDescriptor(name, "", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                    override suspend fun execute(a: JsonObject, c: ToolExecutionContext) = UnifiedToolResult.unsupported(UnifiedToolCall("", "", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null))
                }
                override fun registerAlias(e: String, i: String) {}
                override fun registerBinding(b: ToolBinding) {}
            },
            policyGate = DefaultUnifiedPolicyGate(),
            confirmationCoordinator = object : ConfirmationCoordinator {
                override suspend fun confirmAndExecute(c: UnifiedToolCall, b: ToolBinding, a: JsonObject, d: PolicyDecision.RequiresConfirmation, ctx: ToolExecutionContext, t: PokeClawTool) = UnifiedToolResult.blocked(c, "No")
            },
            executionContext = object : ToolExecutionContext {}
        )

        session.runInteraction(task)

        assertEquals(2, transcriptTracker.size)
        // Turn 1: [UserTextInput]
        assertEquals(1, transcriptTracker[0].size)
        assertTrue(transcriptTracker[0][0] is UserTextInput)

        // Turn 2: [UserTextInput, ModelTextOutput, ModelToolCall, ToolResult]
        val turn2 = transcriptTracker[1]
        assertEquals(4, turn2.size)
        assertTrue(turn2[0] is UserTextInput)
        assertTrue(turn2[1] is ModelTextOutput)
        assertTrue(turn2[2] is ModelToolCall)
        // turn2[3] is the mapped result
    }
}
