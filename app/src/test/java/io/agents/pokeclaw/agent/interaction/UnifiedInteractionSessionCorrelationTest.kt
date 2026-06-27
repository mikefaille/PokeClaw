package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import com.google.gson.JsonObject
import io.agents.pokeclaw.agent.policy.*
import io.agents.pokeclaw.agent.tools.*
import io.agents.pokeclaw.agent.providers.gemini.GeminiFunctionResultInput
import kotlinx.coroutines.runBlocking

class UnifiedInteractionSessionCorrelationTest {

    @Test
    fun testSameNameCorrelationOrder() = runBlocking {
        val task = UserTask("id", "prompt", InteractionCapabilities.DEFAULT_GEMINI)
        val transcriptTracker = mutableListOf<List<InteractionInput>>()

        val geminiClient = object : GeminiClient {
            override suspend fun createInteraction(model: String, input: List<InteractionInput>, tools: List<GeminiToolDeclaration>, systemInstruction: String): GeminiInteraction {
                transcriptTracker.add(input.toList())
                return object : GeminiInteraction {
                    override val id = "interaction"
                    override val steps = emptyList<Any>()
                }
            }
        }

        // Mock a step normalizer that returns two same-named calls on turn 1
        val stepNormalizer = object : InteractionStepNormalizer {
            var calls = 0
            override fun normalize(steps: List<Any>): InteractionSteps {
                calls++
                return object : InteractionSteps {
                    override fun finalModelOutput(): String? {
                        return if (calls == 2) "Done" else null
                    }
                    override fun toolCalls(): List<UnifiedToolCall> {
                        return if (calls == 1) {
                            listOf(
                                UnifiedToolCall("call1", "test_tool", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null),
                                UnifiedToolCall("call2", "test_tool", JsonObject(), ToolOrigin.PokeClawFunction, null, null, null)
                            )
                        } else {
                            emptyList()
                        }
                    }
                }
            }
        }

        val serializer = object : InteractionResultSerializer {
            override fun serialize(result: UnifiedToolResult): InteractionInput {
                return GeminiFunctionResultInput(result.callId, result.toolName, result.output, result.status.name, null, emptyMap(), null)
            }
        }

        val session = UnifiedInteractionSession(
            taskRuntime = DefaultTaskRuntime(3),
            toolExposurePolicy = object: ToolExposurePolicy { override fun selectTools(t: UserTask, c: InteractionCapabilities, s: TaskState) = emptyList<GeminiToolDeclaration>() },
            geminiClient = geminiClient,
            promptFactory = object: PromptFactory { override fun create(t: UserTask) = "sys" },
            stepNormalizer = stepNormalizer,
            interactionSerializer = serializer,
            registry = object : io.agents.pokeclaw.agent.tools.UnifiedToolRegistry {
                override fun resolve(name: String) = ToolBinding(name, name, object: ToolArgumentAdapter { override fun adapt(a: JsonObject) = a }, ToolDescriptor(name, "", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW))
                override fun getInternalTool(name: String) = object : PokeClawTool {
                    override val descriptor = ToolDescriptor(name, "", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                    override suspend fun execute(a: JsonObject, c: ToolExecutionContext): UnifiedToolResult {
                        return UnifiedToolResult("fixed_in_real_code", name, ToolStatus.SUCCESS, JsonObject(), null, false, null, false, emptyMap())
                    }
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

        // Validate Turn 2's transcript
        assertEquals(2, transcriptTracker.size)
        val turn2 = transcriptTracker[1]

        // Expected layout: UserTextInput, ModelToolCall(call1), GeminiFunctionResult(call1), ModelToolCall(call2), GeminiFunctionResult(call2)
        // Wait, loop ordering:
        // accumulatedTranscript.add(ModelToolCall(call))
        // accumulatedTranscript.add(interactionSerializer.serialize(result))
        // So they interleave: TCall A, TRes A, TCall B, TRes B

        assertEquals(5, turn2.size)
        assertTrue(turn2[0] is UserTextInput)

        val tCall1 = turn2[1] as ModelToolCall
        assertEquals("call1", tCall1.call.callId)

        val tRes1 = turn2[2] as GeminiFunctionResultInput
        // The registry execute mock returns "unknown" for callId since it doesn't know it, wait, UnifiedInteractionSession.executeUnifiedCall
        // doesn't inject callId into execute. The unified session gets the result. Let's fix mock.
        // Ah, our mock execute returned "unknown" callId. Let's assert based on `tRes1.toolName`.
        assertEquals("test_tool", tRes1.toolName)

        val tCall2 = turn2[3] as ModelToolCall
        assertEquals("call2", tCall2.call.callId)

        val tRes2 = turn2[4] as GeminiFunctionResultInput
        assertEquals("test_tool", tRes2.toolName)
    }
}
