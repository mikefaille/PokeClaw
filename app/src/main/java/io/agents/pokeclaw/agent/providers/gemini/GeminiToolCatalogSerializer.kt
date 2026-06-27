package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.types.Tool
import com.google.genai.types.FunctionDeclaration
import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration
import io.agents.pokeclaw.utils.XLog

object GeminiToolCatalogSerializer {
    private const val TAG = "GeminiToolCatalog"

    fun serialize(tools: List<GeminiToolDeclaration>): List<Tool> {
        val declarations = mutableListOf<FunctionDeclaration>()

        for (tool in tools) {
            when (tool) {
                is GeminiFunctionDeclarationAdapter -> {
                    declarations.add(tool.declaration)
                }
                else -> {
                    XLog.w(TAG, "Unsupported tool declaration type: \${tool::class.java.name}")
                }
            }
        }

        if (declarations.isEmpty()) return emptyList()

        return listOf(Tool.builder().functionDeclarations(declarations).build())
    }
}

class GeminiFunctionDeclarationAdapter(val declaration: FunctionDeclaration) : GeminiToolDeclaration
