package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.types.GenerateContentResponse
import com.google.gson.Gson
import io.agents.pokeclaw.agent.interaction.InteractionSteps
import io.agents.pokeclaw.agent.interaction.InteractionStepNormalizer
import io.agents.pokeclaw.agent.interaction.UnifiedToolCall
import io.agents.pokeclaw.agent.interaction.ToolOrigin
import java.util.UUID

class GeminiStepParser(private val gson: Gson = Gson()) : InteractionStepNormalizer {
    override fun normalize(steps: List<Any>): InteractionSteps {
        val toolCalls = mutableListOf<UnifiedToolCall>()
        var finalOutput: String? = null

        for (step in steps) {
            if (step is GenerateContentResponse) {
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

                                         toolCalls.add(
                                             UnifiedToolCall(
                                                 callId = UUID.randomUUID().toString(),
                                                 name = name,
                                                 arguments = argsJson,
                                                 origin = ToolOrigin.PokeClawFunction,
                                                 intent = null,
                                                 signature = null,
                                                 safetyDecision = null
                                             )
                                         )
                                     }
                                     val textOpt = part.text()
                                     if (textOpt != null && textOpt.isPresent()) {
                                         val text = textOpt.get()
                                         if (text.isNotBlank()) {
                                             finalOutput = text
                                         }
                                     }
                                 }
                             }
                         }
                     }
                }
            }
        }

        return object : InteractionSteps {
            override fun finalModelOutput(): String? = finalOutput
            override fun toolCalls(): List<UnifiedToolCall> = toolCalls
        }
    }
}
