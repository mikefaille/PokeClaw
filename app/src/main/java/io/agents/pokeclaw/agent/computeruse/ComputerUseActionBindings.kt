package io.agents.pokeclaw.agent.computeruse

import io.agents.pokeclaw.agent.tools.ToolArgumentAdapter
import io.agents.pokeclaw.agent.tools.ToolBinding
import io.agents.pokeclaw.agent.tools.ToolDescriptor
import com.google.gson.JsonObject

class IdentityArgumentAdapter : ToolArgumentAdapter {
    override fun adapt(arguments: JsonObject): JsonObject? {
        return arguments
    }
}

// In a real scenario this would adapt "click" coords to the screen size
class ComputerUseClickAdapter : ToolArgumentAdapter {
    override fun adapt(arguments: JsonObject): JsonObject? {
        return arguments
    }
}
