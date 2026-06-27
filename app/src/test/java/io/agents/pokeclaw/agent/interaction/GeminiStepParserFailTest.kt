package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import io.agents.pokeclaw.agent.providers.gemini.GeminiStepParser

class GeminiStepParserFailTest {
    @Test
    fun testUnsupportedStepIgnoredSafely() {
        var warningLogged = false
        val parser = GeminiStepParser(
            logWarning = { tag, msg -> warningLogged = true }
        )

        val steps = parser.normalize(listOf("Some String that is not a Response"))

        assertTrue("Parser should log a warning for unsupported steps", warningLogged)
        assertNull("Final output should be null for invalid step", steps.finalModelOutput())
        assertTrue("Tool calls should be empty for invalid step", steps.toolCalls().isEmpty())
    }
}
