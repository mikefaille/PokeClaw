package io.agents.pokeclaw.agent.tools

// Adding tool registry interface
interface UnifiedToolRegistry {
    fun registerAlias(externalName: String, internalTool: String)
    fun registerBinding(binding: ToolBinding)
    fun resolve(externalName: String): ToolBinding?
    fun getInternalTool(name: String): PokeClawTool?
}
