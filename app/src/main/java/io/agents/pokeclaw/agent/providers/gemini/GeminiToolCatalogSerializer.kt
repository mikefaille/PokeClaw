package io.agents.pokeclaw.agent.providers.gemini

import com.google.genai.types.Tool
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration

// Implements serialization of generic tool declarations into Gemini-specific `Tool` format
object GeminiToolCatalogSerializer {
    fun serialize(tools: List<GeminiToolDeclaration>): List<Tool> {
        // Assume GeminiToolDeclaration can provide FunctionDeclarations
        val declarations = tools.filterIsInstance<GeminiFunctionDeclarationAdapter>().map { it.declaration }
        if (declarations.isEmpty()) return emptyList()

        return listOf(Tool.builder().functionDeclarations(declarations).build())
    }
}

class GeminiFunctionDeclarationAdapter(val declaration: FunctionDeclaration) : GeminiToolDeclaration
