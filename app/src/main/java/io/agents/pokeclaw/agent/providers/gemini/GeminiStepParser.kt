package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.types.GenerateContentResponse
import com.google.gson.Gson
import io.agents.pokeclaw.agent.interaction.InteractionSteps
import io.agents.pokeclaw.agent.interaction.InteractionStepNormalizer
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.interaction.ToolOrigin
import io.agents.pokeclaw.agent.interaction.SafetyDecision
import java.util.UUID

class GeminiStepParser(
    private val gson: Gson = Gson(),
    private val logWarning: (String, String) -> Unit = { tag, msg -> io.agents.pokeclaw.utils.XLog.w(tag, msg) }
) : InteractionStepNormalizer {
    override fun normalize(steps: List<Any>): InteractionSteps {
        val toolCalls = mutableListOf<UnifiedToolCall>()
        val textBuilder = StringBuilder()

        for (step in steps) {
            when (step) {
                is GenerateContentResponse -> {
                    val candidatesOpt = step.candidates()
                    if (candidatesOpt != null && candidatesOpt.isPresent()) {
                        val candidateList = candidatesOpt.get()
                        if (candidateList.isNotEmpty()) {
                            val contentOpt = candidateList[0].content()
                            if (contentOpt != null && contentOpt.isPresent()) {
                                val content = contentOpt.get()
                                val partsOpt = content.parts()
                                if (partsOpt != null && partsOpt.isPresent()) {
                                    for (part in partsOpt.get()) {
                                        val functionCallOpt = part.functionCall()
                                        if (functionCallOpt != null && functionCallOpt.isPresent()) {
                                            val functionCall = functionCallOpt.get()
                                            val argsMapOpt = functionCall.args()
                                            val argsMap = if (argsMapOpt != null && argsMapOpt.isPresent()) argsMapOpt.get() else emptyMap<String, Any>()
                                            val argsJson = gson.toJsonTree(argsMap).asJsonObject

                                            val nameOpt = functionCall.name()
                                            val name = if (nameOpt != null && nameOpt.isPresent()) nameOpt.get() else "unknown"

                                            // The Java/Kotlin SDK for Gemini does not expose a clear UUID `id` on the functionCall.
                                            // Synthesized Call IDs act as a local trace ID rather than true provider correlation.
                                            // Function responses rely on ordered matching or name-matching when passed back to Gemini.
                                            val callId = UUID.randomUUID().toString()

                                            // Action-level safety logic from candidate finish reason is imprecise.
                                            // If action-level provider safety metadata is unavailable, we explicitly state that
                                            // PokeClaw local policy (UnifiedPolicyGate) remains authoritative.
                                            val safetyDecision: SafetyDecision? = null

                                            toolCalls.add(
                                                UnifiedToolCall(
                                                    callId = callId,
                                                    name = name,
                                                    arguments = argsJson,
                                                    origin = ToolOrigin.PokeClawFunction,
                                                    intent = null,
                                                    signature = null,
                                                    safetyDecision = safetyDecision
                                                )
                                            )
                                        }
                                        val textOpt = part.text()
                                        if (textOpt != null && textOpt.isPresent()) {
                                            val text = textOpt.get()
                                            if (text.isNotBlank()) {
                                                textBuilder.append(text).append("\n")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {
                    logWarning("GeminiStepParser", "Unsupported step type: ${step::class.java.name}")
                }
            }
        }

        val finalOutput = if (textBuilder.isNotEmpty()) textBuilder.toString().trim() else null

        return object : InteractionSteps {
            override fun finalModelOutput(): String? = finalOutput
            override fun toolCalls(): List<UnifiedToolCall> = toolCalls
        }
    }
}
