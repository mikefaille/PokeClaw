package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import io.agents.pokeclaw.agent.providers.gemini.GeminiStepParser
import com.google.genai.types.GenerateContentResponse
import com.google.genai.types.Candidate
import com.google.genai.types.Content
import com.google.genai.types.Part

class GeminiStepParserTest {
    @Test
    fun testTextAggregation() {
        val p1 = Part.builder().text("Hello, ").build()
        val p2 = Part.builder().text("world!").build()

        val content = Content.builder().parts(listOf(p1, p2)).build()
        val candidate = Candidate.builder().content(content).build()
        val response = GenerateContentResponse.builder().candidates(listOf(candidate)).build()

        val parser = GeminiStepParser()
        val steps = parser.normalize(listOf(response))

        // Wait, why did it fail? Let's print out what it gave us in the test
        println("Output: " + steps.finalModelOutput())
        assertEquals("Hello, \nworld!", steps.finalModelOutput()?.trim())
    }
}
