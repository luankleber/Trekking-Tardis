package com.example.yolo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.support.common.FileUtil
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YOLODetector(
    context: Context,
    modelPath: String,
    private val inputSize: Int = 320
) {

    private val interpreter: Interpreter
    private val scoreThreshold = 0.5f

    init {
        val model = FileUtil.loadMappedFile(context, modelPath)
        interpreter = Interpreter(model)
    }

    fun detect(bitmap: Bitmap): List<YoloDetection> {
        val resized = Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)
        val inputBuffer = bitmapToByteBuffer(resized)

        // YOLO output: [1, 300, 6]
        val output = Array(1) { Array(300) { FloatArray(6) } }

        interpreter.run(inputBuffer, output)

        return parseOutput(output[0])
    }

    private fun parseOutput(detections: Array<FloatArray>): List<YoloDetection> {
        val results = mutableListOf<YoloDetection>()

        for (det in detections) {
            val score = det[4]
            if (score < scoreThreshold) continue

            val x1 = det[0]
            val y1 = det[1]
            val x2 = det[2]
            val y2 = det[3]
            val classId = det[5].toInt()

            results.add(
                YoloDetection(
                    boundingBox = RectF(x1, y1, x2, y2),
                    score = score,
                    classId = classId
                )
            )
        }
        return results
    }

    private fun bitmapToByteBuffer(bitmap: Bitmap): ByteBuffer {
        val buffer = ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)

        for (pixel in pixels) {
            buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
            buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
            buffer.putFloat((pixel and 0xFF) / 255f)
        }

        buffer.rewind()
        return buffer
    }
}
