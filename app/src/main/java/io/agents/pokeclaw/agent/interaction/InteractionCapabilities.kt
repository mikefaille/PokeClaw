package io.agents.pokeclaw.agent.interaction

data class InteractionCapabilities(
    val computerUse: Boolean,
    val googleSearch: Boolean,
    val directDeviceTools: Boolean,
    val knowledgeTools: Boolean,
    val communicationTools: Boolean
) {
    companion object {
        val DEFAULT_GEMINI = InteractionCapabilities(
            computerUse = true,
            googleSearch = true,
            directDeviceTools = true,
            knowledgeTools = true,
            communicationTools = false
        )

        val RESEARCH_ONLY = InteractionCapabilities(
            computerUse = false,
            googleSearch = true,
            directDeviceTools = false,
            knowledgeTools = true,
            communicationTools = false
        )
    }
}
