package io.agents.pokeclaw.agent.computeruse

import io.agents.pokeclaw.agent.tools.ToolArgumentAdapter
import com.google.gson.JsonObject
import kotlin.math.max
import kotlin.math.min

class IdentityArgumentAdapter : ToolArgumentAdapter {
    override fun adapt(arguments: JsonObject): JsonObject? {
        return arguments
    }
}

class ComputerUseClickAdapter(
    private val transformer: CoordinateTransformer
) : ToolArgumentAdapter {
    override fun adapt(arguments: JsonObject): JsonObject? {
        if (!arguments.has("x") || !arguments.has("y")) return null

        val rawX = arguments.get("x").asInt
        val rawY = arguments.get("y").asInt

        // Clamp to 0..1000 range
        val clampedX = max(0, min(1000, rawX))
        val clampedY = max(0, min(1000, rawY))

        val screenPoint = transformer.transformToScreen(clampedX, clampedY)

        val adapted = JsonObject()
        adapted.addProperty("x", screenPoint.x)
        adapted.addProperty("y", screenPoint.y)

        // Pass through any other arguments
        for ((key, value) in arguments.entrySet()) {
            if (key != "x" && key != "y") {
                adapted.add(key, value)
            }
        }

        return adapted
    }
}
