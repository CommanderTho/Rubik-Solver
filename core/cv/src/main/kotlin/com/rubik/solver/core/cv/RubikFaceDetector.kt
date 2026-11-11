package com.rubik.solver.core.cv

import android.graphics.Bitmap
import android.graphics.Matrix
import com.rubik.solver.core.model.CubeColor
import org.opencv.android.Utils
import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.Rect
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

data class DetectedSticker(
    val index: Int,
    val color: CubeColor,
    val confidence: Float,
    val isLowConfidence: Boolean,
    val averageHsv: HsvSample
)

data class FaceScanResult(
    val stickers: List<DetectedSticker>,
    val colors: List<CubeColor>,
    val faceMedianBrightness: Float,
    val isLowLight: Boolean,
    val hasGlareRisk: Boolean
) {
    val lowConfidenceIndices: Set<Int>
        get() = stickers.filter { it.isLowConfidence }.map { it.index }.toSet()
}

/**
 * Extracts 9 sticker ROIs from a captured face image using OpenCV,
 * converts RGB -> HSV, applies adaptive low-light/glare mitigation,
 * and classifies the 6 standard Rubik's Cube colors.
 */
class RubikFaceDetector {

    /**
     * Detects 9 sticker colors inside the centered square overlay region of [bitmap].
     *
     * @param bitmap Source frame or captured photo.
     * @param rotationDegrees Rotation to apply so the cube matches screen orientation.
     * @param overlaySizeFraction Fraction of min(width, height) occupied by the 3x3 overlay square.
     */
    fun detectFromBitmap(
        bitmap: Bitmap,
        rotationDegrees: Int = 0,
        overlaySizeFraction: Float = 0.68f
    ): FaceScanResult {
        check(OpenCvInitializer.init()) {
            "OpenCV chưa sẵn sàng trên thiết bị."
        }

        val orientedBitmap = rotateIfNeeded(bitmap, rotationDegrees)
        val rgbaMat = Mat()
        val rgbMat = Mat()
        val hsvMat = Mat()

        try {
            Utils.bitmapToMat(orientedBitmap, rgbaMat)
            Imgproc.cvtColor(rgbaMat, rgbMat, Imgproc.COLOR_RGBA2RGB)

            // Crop centered square corresponding to the CameraX 3x3 overlay grid
            val side = (minOf(rgbMat.cols(), rgbMat.rows()) * overlaySizeFraction)
                .toInt()
                .coerceAtLeast(90)
            val startX = ((rgbMat.cols() - side) / 2).coerceAtLeast(0)
            val startY = ((rgbMat.rows() - side) / 2).coerceAtLeast(0)
            val cropRect = Rect(
                startX,
                startY,
                side.coerceAtMost(rgbMat.cols() - startX),
                side.coerceAtMost(rgbMat.rows() - startY)
            )

            val croppedRgb = Mat(rgbMat, cropRect)
            val resizedRgb = Mat()
            try {
                // Normalize to fixed 300x300 for deterministic ROI geometry & fast CPU sampling
                Imgproc.resize(croppedRgb, resizedRgb, Size(300.0, 300.0), 0.0, 0.0, Imgproc.INTER_AREA)
                Imgproc.GaussianBlur(resizedRgb, resizedRgb, Size(3.0, 3.0), 0.0)
                Imgproc.cvtColor(resizedRgb, hsvMat, Imgproc.COLOR_RGB2HSV)

                return analyzeHsvFaceMat(hsvMat)
            } finally {
                croppedRgb.release()
                resizedRgb.release()
            }
        } finally {
            rgbaMat.release()
            rgbMat.release()
            hsvMat.release()
            if (orientedBitmap !== bitmap) {
                orientedBitmap.recycle()
            }
        }
    }

    private fun analyzeHsvFaceMat(hsvMat: Mat): FaceScanResult {
        // Check global face brightness first for low-light CLAHE enhancement
        val channels = ArrayList<Mat>(3)
        Core.split(hsvMat, channels)
        val vChannel = channels[2]
        val initialMeanV = Core.mean(vChannel).`val`[0].toFloat()
        val isLowLight = initialMeanV < 82f

        val workingHsv = if (isLowLight) {
            val enhanced = Mat()
            val clahe = Imgproc.createCLAHE(2.2, Size(6.0, 6.0))
            val enhancedV = Mat()
            try {
                clahe.apply(vChannel, enhancedV)
                channels[2].release()
                channels[2] = enhancedV
                Core.merge(channels, enhanced)
                enhanced
            } catch (t: Throwable) {
                enhancedV.release()
                enhanced.release()
                hsvMat
            }
        } else {
            hsvMat
        }

        channels.forEach { it.release() }

        try {
            val cellSize = workingHsv.cols() / 3
            val margin = (cellSize * 0.28f).toInt()
            val innerSize = (cellSize - 2 * margin).coerceAtLeast(10)

            val cellSamples = ArrayList<List<HsvSample>>(9)
            val cellMedianVs = ArrayList<Float>(9)
            var glarePixelCount = 0
            var totalPixelCount = 0

            for (row in 0..2) {
                for (col in 0..2) {
                    val roiRect = Rect(
                        col * cellSize + margin,
                        row * cellSize + margin,
                        innerSize,
                        innerSize
                    )
                    val roi = Mat(workingHsv, roiRect)
                    try {
                        val pixels = extractSamples(roi)
                        cellSamples += pixels
                        val sortedV = pixels.map { it.v }.sorted()
                        cellMedianVs += sortedV[sortedV.size / 2]
                        glarePixelCount += pixels.count { it.v > 235f && it.s < 32f }
                        totalPixelCount += pixels.size
                    } finally {
                        roi.release()
                    }
                }
            }

            val faceMedianV = cellMedianVs.sorted()[cellMedianVs.size / 2]
            val stickers = cellSamples.mapIndexed { index, pixels ->
                val result = HsvColorClassifier.classifyPatch(pixels, faceMedianV)
                DetectedSticker(
                    index = index,
                    color = result.color,
                    confidence = result.confidence,
                    isLowConfidence = result.isLowConfidence,
                    averageHsv = result.representativeHsv
                )
            }

            val glareRatio = if (totalPixelCount > 0) glarePixelCount.toFloat() / totalPixelCount else 0f
            return FaceScanResult(
                stickers = stickers,
                colors = stickers.map { it.color },
                faceMedianBrightness = faceMedianV,
                isLowLight = isLowLight,
                hasGlareRisk = glareRatio > 0.22f
            )
        } finally {
            if (workingHsv !== hsvMat) {
                workingHsv.release()
            }
        }
    }

    private fun extractSamples(roi: Mat): List<HsvSample> {
        val step = 2
        val rows = roi.rows()
        val cols = roi.cols()
        val byteBuffer = ByteArray(rows * cols * roi.channels())
        val continuous = if (roi.isContinuous) roi else roi.clone()
        try {
            if (continuous.type() != CvType.CV_8UC3) {
                val converted = Mat()
                continuous.convertTo(converted, CvType.CV_8UC3)
                converted.get(0, 0, byteBuffer)
                converted.release()
            } else {
                continuous.get(0, 0, byteBuffer)
            }
        } finally {
            if (continuous !== roi) {
                continuous.release()
            }
        }

        val samples = ArrayList<HsvSample>((rows / step + 1) * (cols / step + 1))
        for (r in 0 until rows step step) {
            for (c in 0 until cols step step) {
                val idx = (r * cols + c) * 3
                val h = (byteBuffer[idx].toInt() and 0xFF).toFloat()
                val s = (byteBuffer[idx + 1].toInt() and 0xFF).toFloat()
                val v = (byteBuffer[idx + 2].toInt() and 0xFF).toFloat()
                samples += HsvSample(h, s, v)
            }
        }
        return samples
    }

    private fun rotateIfNeeded(bitmap: Bitmap, rotationDegrees: Int): Bitmap {
        val normalized = ((rotationDegrees % 360) + 360) % 360
        if (normalized == 0) return bitmap
        val matrix = Matrix().apply { postRotate(normalized.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }
}
