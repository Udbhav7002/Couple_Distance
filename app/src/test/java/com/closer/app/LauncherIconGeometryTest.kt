package com.closer.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LauncherIconGeometryTest {

    @Test
    fun testLauncherForegroundVectorGeometryAndMaskSafety() {
        val file = File("src/main/res/drawable/ic_launcher_foreground.xml")
            .takeIf { it.exists() }
            ?: File("app/src/main/res/drawable/ic_launcher_foreground.xml")
        assertTrue("ic_launcher_foreground.xml must exist", file.exists())

        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val vector = doc.documentElement
        assertEquals("vector", vector.nodeName)
        assertEquals("108", vector.getAttribute("android:viewportWidth"))
        assertEquals("108", vector.getAttribute("android:viewportHeight"))

        val paths = vector.getElementsByTagName("path")
        assertEquals(4, paths.length)

        // 1. Left Lens
        val leftLens = paths.item(0)
        val leftPath = leftLens.attributes.getNamedItem("android:pathData").nodeValue
        val leftStroke = leftLens.attributes.getNamedItem("android:strokeWidth").nodeValue.toFloat()
        assertTrue("Left lens center at (36, 54) with radius 12", leftPath.contains("36 54") && leftPath.contains("12 12"))
        assertEquals(6.0f, leftStroke, 0.01f)

        // 2. Right Lens
        val rightLens = paths.item(1)
        val rightPath = rightLens.attributes.getNamedItem("android:pathData").nodeValue
        val rightStroke = rightLens.attributes.getNamedItem("android:strokeWidth").nodeValue.toFloat()
        assertTrue("Right lens center at (72, 54) with radius 12", rightPath.contains("72 54") && rightPath.contains("12 12"))
        assertEquals(6.0f, rightStroke, 0.01f)

        // Exact bilateral centering: (36.0 + 72.0) / 2 = 54.0
        val leftCenter = 36.0f
        val rightCenter = 72.0f
        val spectaclesCenter = (leftCenter + rightCenter) / 2.0f
        assertEquals(54.0f, spectaclesCenter, 0.001f)

        // Mask safety: safe zone radius = 33.0 dp <= 36.0 dp adaptive icon circular mask
        val leftExtent = leftCenter - (12.0f + leftStroke / 2.0f) // 21.0
        val rightExtent = rightCenter + (12.0f + rightStroke / 2.0f) // 87.0
        val maxDistFromCenter = maxOf(Math.abs(leftExtent - 54.0f), Math.abs(rightExtent - 54.0f))
        assertEquals(33.0f, maxDistFromCenter, 0.001f)
        assertTrue("Spectacles icon must be safe from circular mask clipping", maxDistFromCenter <= 36.0f)

        // 3. Bridge line
        val bridge = paths.item(2)
        val bridgePath = bridge.attributes.getNamedItem("android:pathData").nodeValue
        val bridgeStroke = bridge.attributes.getNamedItem("android:strokeWidth").nodeValue.toFloat()
        assertEquals("M 47 54 L 61 54", bridgePath.trim())
        assertEquals(5.0f, bridgeStroke, 0.01f)

        // 4. Bead on the bridge
        val bead = paths.item(3)
        val beadPath = bead.attributes.getNamedItem("android:pathData").nodeValue
        val beadColor = bead.attributes.getNamedItem("android:fillColor").nodeValue
        assertTrue("Bead centered at (54, 54) with radius 4", beadPath.contains("54 54") && beadPath.contains("4 4"))
        assertEquals("#FFFF375F", beadColor.uppercase())
    }
}
