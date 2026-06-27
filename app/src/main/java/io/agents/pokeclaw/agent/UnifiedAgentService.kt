package io.agents.pokeclaw.agent

import io.agents.pokeclaw.agent.interaction.*
import io.agents.pokeclaw.agent.policy.*
import io.agents.pokeclaw.agent.providers.gemini.*
import io.agents.pokeclaw.agent.tools.*
import io.agents.pokeclaw.utils.XLog
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean
import com.google.gson.JsonObject

class UnifiedAgentService : AgentService {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isRunning = AtomicBoolean(false)
    private var config: AgentConfig? = null
    private var currentJob: Job? = null

    override fun initialize(config: AgentConfig) {
        this.config = config
    }

    override fun updateConfig(config: AgentConfig) {
        this.config = config
    }

    override fun isRunning(): Boolean = isRunning.get()

    override fun cancel() {
        isRunning.set(false)
        currentJob?.cancel()
    }

    override fun shutdown() {
        cancel()
        scope.cancel()
    }

    override fun executeTask(userPrompt: String, callback: AgentCallback) {
        val currentConfig = config ?: throw IllegalStateException("AgentService not initialized")

        if (isRunning.getAndSet(true)) {
            callback.onError(0, IllegalStateException("Agent is already running a task"), 0)
            return
        }

        currentJob = scope.launch {
            try {
                // Dependency Injection / Wiring
                val task = UserTask(
                    id = java.util.UUID.randomUUID().toString(),
                    prompt = userPrompt,
                    capabilities = InteractionCapabilities.DEFAULT_GEMINI
                )

                val runtime = object : TaskRuntime {
                    private var iters = 0
                    override val state = DefaultTaskState()
                    override fun canContinue(): Boolean {
                        iters++
                        return isRunning.get() && iters <= currentConfig.maxIterations
                    }
                }

                val geminiClient = GeminiInteractionsClient(currentConfig.apiKey)
                val stepNormalizer = GeminiStepParser()
                val serializer = GeminiFunctionResultSerializer()

                val registry = object : UnifiedToolRegistry {
                    override fun registerAlias(externalName: String, internalTool: String) {}
                    override fun registerBinding(binding: ToolBinding) {}
                    override fun resolve(externalName: String): ToolBinding? {
                        val internalTool = io.agents.pokeclaw.tool.ToolRegistry.getInstance().getTool(externalName)
                        if (internalTool != null) {
                            return ToolBinding(
                                externalName = externalName,
                                internalName = externalName,
                                argumentAdapter = io.agents.pokeclaw.agent.computeruse.IdentityArgumentAdapter(),
                                descriptor = ToolDescriptor(externalName, internalTool.getDisplayName(), JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                            )
                        }
                        // Handle native finish
                        if (externalName == "finish") {
                             return ToolBinding(
                                externalName = "finish",
                                internalName = "finish",
                                argumentAdapter = io.agents.pokeclaw.agent.computeruse.IdentityArgumentAdapter(),
                                descriptor = ToolDescriptor("finish", "Finish Task", JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                            )
                        }
                        return null
                    }
                    override fun getInternalTool(name: String): PokeClawTool? {
                        return object : PokeClawTool {
                            override val descriptor = ToolDescriptor(name, name, JsonObject(), ToolExposure.ALWAYS, ToolRisk.LOW)
                            override suspend fun execute(arguments: JsonObject, context: ToolExecutionContext): UnifiedToolResult {
                                if (name == "finish") {
                                     return UnifiedToolResult("c1", "finish", ToolStatus.SUCCESS, arguments, null, false, null, false, emptyMap())
                                }
                                val params = com.google.gson.Gson().fromJson(arguments, Map::class.java) as Map<String, Any>
                                val res = io.agents.pokeclaw.tool.ToolRegistry.getInstance().executeTool(name, params)

                                val status = if (res.isSuccess) ToolStatus.SUCCESS else ToolStatus.FAILED
                                val error = if (!res.isSuccess) io.agents.pokeclaw.agent.interaction.ToolError("EXEC_ERR", res.error ?: "Unknown error") else null
                                val output = JsonObject().apply { addProperty("output", res.data ?: "success") }

                                return UnifiedToolResult("c1", name, status, output, error, false, null, false, emptyMap())
                            }
                        }
                    }
                }

                val policyGate = DefaultUnifiedPolicyGate()
                val exposurePolicy = object : ToolExposurePolicy {
                    override fun selectTools(task: UserTask, capabilities: InteractionCapabilities, state: TaskState): List<GeminiToolDeclaration> {
                        // Expose standard computer use + finish
                        return listOf(
                            io.agents.pokeclaw.agent.computeruse.ComputerUseToolDeclarations.clickDeclaration(),
                            io.agents.pokeclaw.agent.computeruse.ComputerUseToolDeclarations.typeDeclaration(),
                            io.agents.pokeclaw.agent.computeruse.ComputerUseToolDeclarations.goBackDeclaration(),
                            io.agents.pokeclaw.agent.computeruse.ComputerUseToolDeclarations.finishDeclaration()
                        )
                    }
                }

                val promptFactory = object : PromptFactory {
                    override fun create(task: UserTask): String {
                        return currentConfig.systemPrompt
                    }
                }

                val confirmationCoordinator = object : ConfirmationCoordinator {
                    override suspend fun confirmAndExecute(call: UnifiedToolCall, binding: ToolBinding, arguments: JsonObject, decision: PolicyDecision.RequiresConfirmation, context: ToolExecutionContext, internalTool: PokeClawTool): UnifiedToolResult {
                        return UnifiedToolResult.blocked(call, "Confirmation auto-denied in background service")
                    }
                }

                val executionContext = object : ToolExecutionContext {}

                val session = UnifiedInteractionSession(
                    taskRuntime = runtime,
                    toolExposurePolicy = exposurePolicy,
                    geminiClient = geminiClient,
                    promptFactory = promptFactory,
                    stepNormalizer = stepNormalizer,
                    interactionSerializer = serializer,
                    registry = registry,
                    policyGate = policyGate,
                    confirmationCoordinator = confirmationCoordinator,
                    executionContext = executionContext
                )

                val result = session.runInteraction(task)

                when (result) {
                    is TaskResult.Success -> callback.onComplete(0, result.output, 0)
                    is TaskResult.Failure -> callback.onError(0, Exception("Task failed: ${result.reason}"), 0)
                    is TaskResult.Blocked -> callback.onError(0, Exception("Task blocked: ${result.reason}"), 0)
                    is TaskResult.Cancelled -> callback.onComplete(0, "Cancelled", 0)
                    is TaskResult.IterationLimitReached -> callback.onError(0, Exception("Iteration limit reached"), 0)
                }

            } catch (e: CancellationException) {
                callback.onComplete(0, "Cancelled via UI", 0)
            } catch (e: Exception) {
                XLog.e("UnifiedAgentService", "Execution error", e)
                callback.onError(0, e, 0)
            } finally {
                isRunning.set(false)
            }
        }
    }
}
