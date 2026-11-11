package com.rubik.solver.core.cv

import com.rubik.solver.core.model.CubeColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HsvColorClassifierTest {

    @Test
    fun `classifies standard six colors accurately`() {
        assertEquals(CubeColor.WHITE, HsvColorClassifier.classifySingle(HsvSample(0f, 15f, 220f)).color)
        assertEquals(CubeColor.YELLOW, HsvColorClassifier.classifySingle(HsvSample(28f, 200f, 220f)).color)
        assertEquals(CubeColor.RED, HsvColorClassifier.classifySingle(HsvSample(2f, 210f, 200f)).color)
        assertEquals(CubeColor.RED, HsvColorClassifier.classifySingle(HsvSample(176f, 210f, 200f)).color)
        assertEquals(CubeColor.ORANGE, HsvColorClassifier.classifySingle(HsvSample(13f, 220f, 220f)).color)
        assertEquals(CubeColor.GREEN, HsvColorClassifier.classifySingle(HsvSample(62f, 190f, 180f)).color)
        assertEquals(CubeColor.BLUE, HsvColorClassifier.classifySingle(HsvSample(110f, 210f, 190f)).color)
    }

    @Test
    fun `rejects specular glare highlights inside a red sticker patch`() {
        val patch = buildList {
            repeat(70) { add(HsvSample(177f, 215f, 195f)) }
            // 30% bright white specular glare on glossy plastic
            repeat(30) { add(HsvSample(0f, 10f, 252f)) }
        }
        val result = HsvColorClassifier.classifyPatch(patch, faceMedianV = 190f)
        assertEquals(CubeColor.RED, result.color)
        assertTrue(result.confidence > 0.6f)
    }

    @Test
    fun `handles low light white vs yellow separation`() {
        val dimWhite = HsvColorClassifier.classifySingle(
            HsvSample(25f, 35f, 95f),
            faceMedianV = 85f
        )
        val dimYellow = HsvColorClassifier.classifySingle(
            HsvSample(28f, 175f, 105f),
            faceMedianV = 85f
        )
        assertEquals(CubeColor.WHITE, dimWhite.color)
        assertEquals(CubeColor.YELLOW, dimYellow.color)
    }

    @Test
    fun `flags ambiguous orange-red boundary as low confidence`() {
        val borderline = HsvColorClassifier.classifySingle(HsvSample(8f, 180f, 180f))
        assertTrue(borderline.isLowConfidence)
    }
}
