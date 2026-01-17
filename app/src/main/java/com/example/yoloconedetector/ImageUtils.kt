package com.example.yoloconedetector

import android.graphics.Bitmap
import java.nio.ByteBuffer
import java.nio.ByteOrder

object ImageUtils {

    fun bitmapToByteBuffer(
        bitmap: Bitmap,
        inputSize: Int
    ): ByteBuffer {

        val buffer =
            ByteBuffer.allocateDirect(1 * inputSize * inputSize * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val resized =
            Bitmap.createScaledBitmap(bitmap, inputSize, inputSize, true)

        for (y in 0 until inputSize) {
            for (x in 0 until inputSize) {
                val px = resized.getPixel(x, y)

                buffer.putFloat(((px shr 16 and 0xFF) / 255f))
                buffer.putFloat(((px shr 8 and 0xFF) / 255f))
                buffer.putFloat(((px and 0xFF) / 255f))
            }
        }

        buffer.rewind()
        return buffer
    }
}
