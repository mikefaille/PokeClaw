package io.agents.pokeclaw.agent.computeruse

import android.graphics.Point

interface CoordinateTransformer {
    fun transformToScreen(x: Int, y: Int): Point
}

class DefaultCoordinateTransformer(
    private val screenWidth: Int,
    private val screenHeight: Int
) : CoordinateTransformer {
    override fun transformToScreen(x: Int, y: Int): Point {
        // Gemini 2.0 (and Anthropic Claude) Computer Use API represents spatial
        // coordinates scaled to a 1000x1000 grid relative to the image size.
        return Point((x * screenWidth) / 1000, (y * screenHeight) / 1000)
    }
}
