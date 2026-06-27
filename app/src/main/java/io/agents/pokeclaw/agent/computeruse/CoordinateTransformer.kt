package io.agents.pokeclaw.agent.computeruse

import android.graphics.Point
import kotlin.math.max
import kotlin.math.min

interface CoordinateTransformer {
    fun transformToScreen(x: Int, y: Int): Point
}

class DefaultCoordinateTransformer(
    private val screenWidth: Int,
    private val screenHeight: Int
) : CoordinateTransformer {
    override fun transformToScreen(x: Int, y: Int): Point {
        if (screenWidth <= 0 || screenHeight <= 0) {
            return Point(0, 0)
        }

        // Clamp to 1000 bounds
        val clampedX = max(0, min(1000, x))
        val clampedY = max(0, min(1000, y))

        // Gemini 2.0 (and Anthropic Claude) Computer Use API represents spatial
        // coordinates scaled to a 1000x1000 grid relative to the image size.
        val targetX = (clampedX * screenWidth) / 1000
        val targetY = (clampedY * screenHeight) / 1000

        // Final physical clamping to bounds (0..width-1, 0..height-1)
        val finalX = max(0, min(screenWidth - 1, targetX))
        val finalY = max(0, min(screenHeight - 1, targetY))

        return Point(finalX, finalY)
    }
}
