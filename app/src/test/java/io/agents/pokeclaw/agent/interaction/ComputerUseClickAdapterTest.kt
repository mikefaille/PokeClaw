package io.agents.pokeclaw.agent.interaction

import org.junit.Test
import org.junit.Assert.*
import io.agents.pokeclaw.agent.computeruse.ComputerUseClickAdapter
import io.agents.pokeclaw.agent.computeruse.DefaultCoordinateTransformer
import io.agents.pokeclaw.agent.computeruse.ScreenCoordinate
import android.graphics.Point
import com.google.gson.JsonObject

class ComputerUseClickAdapterTest {

    @Test
    fun testClampingAndConversion() {
        val transformer = object : io.agents.pokeclaw.agent.computeruse.CoordinateTransformer {
            override fun transformToScreen(x: Int, y: Int): Point {
                val point = Point()
                val logic = DefaultCoordinateTransformer(2000, 3000)
                val coord = logic.calculateScreenCoordinate(x, y)
                point.x = coord.x
                point.y = coord.y
                return point
            }
        }
        val adapter = ComputerUseClickAdapter(transformer)

        val args1 = JsonObject().apply {
            addProperty("x", 1200)
            addProperty("y", 1500)
        }
        val res1 = adapter.adapt(args1)!!
        assertEquals(1999, res1.get("x").asInt)
        assertEquals(2999, res1.get("y").asInt)

        val args2 = JsonObject().apply {
            addProperty("x", -50)
            addProperty("y", -10)
        }
        val res2 = adapter.adapt(args2)!!
        assertEquals(0, res2.get("x").asInt)
        assertEquals(0, res2.get("y").asInt)

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

    @Test
    fun testTransformerDirectly() {
        val transformer = DefaultCoordinateTransformer(2000, 3000)

        val pointZero = transformer.calculateScreenCoordinate(0, 0)
        assertEquals(0, pointZero.x)
        assertEquals(0, pointZero.y)

        val pointMax = transformer.calculateScreenCoordinate(1000, 1000)
        assertEquals(1999, pointMax.x)
        assertEquals(2999, pointMax.y)

        val pointMid = transformer.calculateScreenCoordinate(500, 500)
        assertEquals(1000, pointMid.x)
        assertEquals(1500, pointMid.y)

        val pointNegativeBounds = DefaultCoordinateTransformer(0, -10).calculateScreenCoordinate(500, 500)
        assertEquals(0, pointNegativeBounds.x)
        assertEquals(0, pointNegativeBounds.y)
    }
}
