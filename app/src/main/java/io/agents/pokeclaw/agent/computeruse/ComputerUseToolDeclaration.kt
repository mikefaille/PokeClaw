package io.agents.pokeclaw.agent.computeruse

import io.agents.pokeclaw.agent.interaction.GeminiToolDeclaration
import com.google.genai.types.FunctionDeclaration
import com.google.genai.types.Schema
import io.agents.pokeclaw.agent.providers.gemini.GeminiFunctionDeclarationAdapter

object ComputerUseToolDeclarations {
    fun clickDeclaration(): GeminiToolDeclaration {
        return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("click")
                .description("Click on a specific coordinate on the screen")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "x" to Schema.builder().type("INTEGER").description("X coordinate (0-1000)").build(),
                                "y" to Schema.builder().type("INTEGER").description("Y coordinate (0-1000)").build()
                            )
                        )
                        .required(listOf("x", "y"))
                        .build()
                )
                .build()
        )
    }

    fun typeDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("type")
                .description("Type text into the focused field")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "text" to Schema.builder().type("STRING").description("Text to type").build()
                            )
                        )
                        .required(listOf("text"))
                        .build()
                )
                .build()
        )
    }
}
