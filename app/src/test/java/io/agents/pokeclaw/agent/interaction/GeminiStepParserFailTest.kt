package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import io.agents.pokeclaw.agent.providers.gemini.GeminiStepParser

class GeminiStepParserFailTest {
    @Test
    fun testUnsupportedStepIgnored() {
        val parser = GeminiStepParser()
        val steps = parser.normalize(listOf("Some String that is not a Response"))
        assertNull(steps.finalModelOutput())
        assertTrue(steps.toolCalls().isEmpty())
    }
}
