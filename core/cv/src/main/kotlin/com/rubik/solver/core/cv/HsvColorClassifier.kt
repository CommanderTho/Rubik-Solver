package com.rubik.solver.core.cv

import com.rubik.solver.core.model.CubeColor
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * Single HSV pixel in OpenCV scale: H in [0, 180], S in [0, 255], V in [0, 255].
 */
data class HsvSample(
    val h: Float,
    val s: Float,
    val v: Float
)

data class ColorClassification(
    val color: CubeColor,
    val confidence: Float,
    val isLowConfidence: Boolean,
    val representativeHsv: HsvSample
)

/**
 * Robust HSV color classifier tailored for Rubik's Cube stickers under challenging
 * real-world conditions:
 * - Specular glare / reflections (phản quang): filters washed-out highlights when chromatic pixels exist.
 * - Low light (ánh sáng yếu): uses adaptive relative saturation/value thresholds and circular hue stats.
 * - Black sticker borders: trims dark pixels before computing median HSV.
 */
object HsvColorClassifier {

    /**
     * Classifies a patch of HSV pixels sampled from the inner ROI of one sticker cell.
     *
     * @param pixels Raw HSV pixels inside the sticker ROI.
     * @param faceMedianV Median V across all 9 cells of the captured face for low-light adaptation.
     */
    fun classifyPatch(
        pixels: List<HsvSample>,
        faceMedianV: Float = 150f
    ): ColorClassification {
        if (pixels.isEmpty()) {
            return ColorClassification(
                color = CubeColor.WHITE,
                confidence = 0f,
                isLowConfidence = true,
                representativeHsv = HsvSample(0f, 0f, 0f)
            )
        }

        // 1. Remove black plastic border pixels if brighter sticker pixels exist
        val darkCutoff = (faceMedianV * 0.25f).coerceIn(18f, 45f)
        val nonDark = pixels.filter { it.v >= darkCutoff }
        val usable = if (nonDark.size >= (pixels.size * 0.25f).toInt().coerceAtLeast(1)) {
            nonDark
        } else {
            pixels
        }

        // 2. Specular glare rejection: drop high-V / very-low-S reflections if chromatic pixels dominate
        val chromatic = usable.filter { !(it.v > 228f && it.s < 38f) }
        val filtered = if (chromatic.size >= (usable.size * 0.35f).toInt().coerceAtLeast(1)) {
            chromatic
        } else {
            usable
        }

        // 3. Robust trimmed median (circular mean for Hue to handle red 0/180 wrap-around)
        val repH = circularHueMean(filtered)
        val repS = median(filtered.map { it.s })
        val repV = median(filtered.map { it.v })
        val rep = HsvSample(repH, repS, repV)

        return classifySingle(rep, faceMedianV)
    }

    fun classifySingle(
        hsv: HsvSample,
        faceMedianV: Float = 150f
    ): ColorClassification {
        val h = ((hsv.h % 180f) + 180f) % 180f
        val s = hsv.s.coerceIn(0f, 255f)
        val v = hsv.v.coerceIn(0f, 255f)

        // Adaptive White detection:
        // Under normal light White has low saturation (S < 60) and decent V.
        // Under low light, White's V drops (e.g. 75..130) while maintaining low S/V ratio.
        val whiteSatLimit = if (faceMedianV < 95f) 66f else 58f
        val whiteMinV = if (faceMedianV < 95f) 55f else 80f
        val saturationToValueRatio = s / v.coerceAtLeast(1f)

        if (s <= whiteSatLimit && v >= whiteMinV && saturationToValueRatio <= 0.45f) {
            val confidence = (1f - (s / whiteSatLimit) * 0.5f).coerceIn(0.45f, 0.99f)
            return ColorClassification(
                color = CubeColor.WHITE,
                confidence = confidence,
                isLowConfidence = confidence < 0.65f,
                representativeHsv = hsv
            )
        }

        // Chromatic colors classified by Hue distance to calibrated OpenCV Hue centroids
        // Red wraps around 0 / 180; Orange ~ 13; Yellow ~ 28; Green ~ 60; Blue ~ 110
        val candidates = listOf(
            CubeColor.RED to circularHueDistance(h, 2f),
            CubeColor.ORANGE to circularHueDistance(h, 13f),
            CubeColor.YELLOW to circularHueDistance(h, 28f),
            CubeColor.GREEN to circularHueDistance(h, 62f),
            CubeColor.BLUE to circularHueDistance(h, 110f)
        ).sortedBy { it.second }

        val best = candidates[0]
        val second = candidates[1]

        // Margin between best and second-best hue centroid drives confidence
        val margin = (second.second - best.second).coerceAtLeast(0f)
        val hueScore = (1f - (best.second / 32f)).coerceIn(0.15f, 1f)
        val satPenalty = if (s < 75f) 0.78f else 1f
        val lowLightPenalty = if (v < 45f) 0.75f else 1f
        val marginFactor = (margin / 10f).coerceIn(0.5f, 1f)

        val confidence = (hueScore * satPenalty * lowLightPenalty * marginFactor).coerceIn(0.2f, 0.99f)

        return ColorClassification(
            color = best.first,
            confidence = confidence,
            isLowConfidence = confidence < 0.62f,
            representativeHsv = hsv
        )
    }

    private fun circularHueMean(samples: List<HsvSample>): Float {
        var sumCos = 0.0
        var sumSin = 0.0
        for (sample in samples) {
            // OpenCV Hue [0, 180] maps to [0, 2*PI]
            val rad = (sample.h / 180.0) * 2.0 * PI
            val weight = (sample.s / 255.0).coerceAtLeast(0.1)
            sumCos += cos(rad) * weight
            sumSin += sin(rad) * weight
        }
        val angle = atan2(sumSin, sumCos)
        val normalized = if (angle < 0) angle + 2.0 * PI else angle
        return ((normalized / (2.0 * PI)) * 180.0).toFloat()
    }

    private fun circularHueDistance(h1: Float, h2: Float): Float {
        val diff = abs(h1 - h2)
        return min(diff, 180f - diff)
    }

    private fun median(values: List<Float>): Float {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2f
        } else {
            sorted[mid]
        }
    }
}
