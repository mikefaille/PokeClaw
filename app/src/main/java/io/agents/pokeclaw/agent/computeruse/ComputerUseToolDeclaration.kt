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

    fun goBackDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("go_back")
                .description("Press the system back button")
                .parameters(Schema.builder().type("OBJECT").properties(emptyMap()).build())
                .build()
        )
    }

    fun pressKeyDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("press_key")
                .description("Press a system key (home, back, enter)")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "key" to Schema.builder().type("STRING").description("Key to press").build()
                            )
                        )
                        .required(listOf("key"))
                        .build()
                )
                .build()
        )
    }

    fun finishDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("finish")
                .description("End the task and return a summary of the outcome")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "summary" to Schema.builder().type("STRING").description("Summary of the outcome").build()
                            )
                        )
                        .required(listOf("summary"))
                        .build()
                )
                .build()
        )
    }

    fun openAppDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("open_app")
                .description("Open an application by package name")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "package_name" to Schema.builder().type("STRING").description("Package name of the app").build()
                            )
                        )
                        .required(listOf("package_name"))
                        .build()
                )
                .build()
        )
    }

    fun listAppsDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("list_apps")
                .description("List installed applications")
                .parameters(Schema.builder().type("OBJECT").properties(emptyMap()).build())
                .build()
        )
    }

    fun takeScreenshotDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("take_screenshot")
                .description("Take a new screenshot of the current screen")
                .parameters(Schema.builder().type("OBJECT").properties(emptyMap()).build())
                .build()
        )
    }

    fun longPressDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("long_press")
                .description("Long press on a specific coordinate on the screen")
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

    fun dragAndDropDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("drag_and_drop")
                .description("Drag from a start coordinate and drop at an end coordinate")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "start_x" to Schema.builder().type("INTEGER").description("Start X coordinate (0-1000)").build(),
                                "start_y" to Schema.builder().type("INTEGER").description("Start Y coordinate (0-1000)").build(),
                                "end_x" to Schema.builder().type("INTEGER").description("End X coordinate (0-1000)").build(),
                                "end_y" to Schema.builder().type("INTEGER").description("End Y coordinate (0-1000)").build()
                            )
                        )
                        .required(listOf("start_x", "start_y", "end_x", "end_y"))
                        .build()
                )
                .build()
        )
    }

    fun waitDeclaration(): GeminiToolDeclaration {
         return GeminiFunctionDeclarationAdapter(
            FunctionDeclaration.builder()
                .name("wait")
                .description("Wait for a specified duration in milliseconds")
                .parameters(
                    Schema.builder()
                        .type("OBJECT")
                        .properties(
                            mapOf(
                                "duration_ms" to Schema.builder().type("INTEGER").description("Duration to wait in milliseconds").build()
                            )
                        )
                        .required(listOf("duration_ms"))
                        .build()
                )
                .build()
        )
    }
}
