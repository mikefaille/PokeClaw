package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.types.GenerateContentResponse
import com.google.gson.Gson
import io.agents.pokeclaw.agent.interaction.InteractionSteps
import io.agents.pokeclaw.agent.interaction.InteractionStepNormalizer
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.interaction.ToolOrigin
import io.agents.pokeclaw.agent.interaction.SafetyDecision
import java.util.UUID
import java.security.MessageDigest

class GeminiStepParser(private val gson: Gson = Gson()) : InteractionStepNormalizer {
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
                                    var callIndex = 0
                                    for (part in partsOpt.get()) {
                                        val functionCallOpt = part.functionCall()
                                        if (functionCallOpt != null && functionCallOpt.isPresent()) {
                                            val functionCall = functionCallOpt.get()
                                            val argsMapOpt = functionCall.args()
                                            val argsMap = if (argsMapOpt != null && argsMapOpt.isPresent()) argsMapOpt.get() else emptyMap<String, Any>()
                                            val argsJson = gson.toJsonTree(argsMap).asJsonObject

                                            val nameOpt = functionCall.name()
                                            val name = if (nameOpt != null && nameOpt.isPresent()) nameOpt.get() else "unknown"

                                            val idHash = "${name}-${argsJson.toString()}-${callIndex}"
                                            val callId = hashString(idHash)

                                            var safetyDecision: SafetyDecision? = null
                                            val finishReasonOpt = candidateList[0].finishReason()
                                            if (finishReasonOpt != null && finishReasonOpt.isPresent()) {
                                                if (finishReasonOpt.get().toString().contains("SAFETY")) {
                                                     safetyDecision = SafetyDecision.Blocked
                                                }
                                            }

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
                                            callIndex++
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
                    io.agents.pokeclaw.utils.XLog.w("GeminiStepParser", "Unsupported step type: ${step::class.java.name}")
                }
            }
        }

        val finalOutput = if (textBuilder.isNotEmpty()) textBuilder.toString().trim() else null

        return object : InteractionSteps {
            override fun finalModelOutput(): String? = finalOutput
            override fun toolCalls(): List<UnifiedToolCall> = toolCalls
        }
    }

    private fun hashString(input: String): String {
        return try {
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(input.toByteArray())
            digest.joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            UUID.randomUUID().toString()
        }
    }
}
