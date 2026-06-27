package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import io.agents.pokeclaw.agent.computeruse.ComputerUseClickAdapter
import io.agents.pokeclaw.agent.computeruse.CoordinateTransformer
import android.graphics.Point
import com.google.gson.JsonObject

class ComputerUseClickAdapterTest {

    @Test
    fun testClampingAndConversion() {
        val transformer = object : CoordinateTransformer {
            override fun transformToScreen(x: Int, y: Int): Point {
                // Return a Point via reflection or mock to avoid stub! exceptions
                val point = Point()
                point.x = x * 2
                point.y = y * 3
                return point
            }
        }
        val adapter = ComputerUseClickAdapter(transformer)

        // Case 1: Out of bounds high
        val args1 = JsonObject().apply {
            addProperty("x", 1200)
            addProperty("y", 1500)
        }
        val res1 = adapter.adapt(args1)!!
        assertEquals(2000, res1.get("x").asInt) // max 1000 * 2
        assertEquals(3000, res1.get("y").asInt) // max 1000 * 3

        // Case 2: Out of bounds low
        val args2 = JsonObject().apply {
            addProperty("x", -50)
            addProperty("y", -10)
        }
        val res2 = adapter.adapt(args2)!!
        assertEquals(0, res2.get("x").asInt) // min 0 * 2
        assertEquals(0, res2.get("y").asInt) // min 0 * 3

        // Case 3: In bounds
        val args3 = JsonObject().apply {
            addProperty("x", 500)
            addProperty("y", 500)
            addProperty("other", "keepme")
        }
        val res3 = adapter.adapt(args3)!!
        assertEquals(1000, res3.get("x").asInt)
        assertEquals(1500, res3.get("y").asInt)
        assertEquals("keepme", res3.get("other").asString)
    }
}
