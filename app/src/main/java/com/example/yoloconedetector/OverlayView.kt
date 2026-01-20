package com.example.yolo

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View

class OverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private var results: List<YoloDetection> = emptyList()
    private val paint = Paint().apply {
        color = Color.rgb(187, 0, 255)
        style = Paint.Style.STROKE
        strokeWidth = 6f
    }

    fun setResults(detections: List<YoloDetection>) {
        results = detections
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        for (det in results) {
            val box = det.boundingBox

            val left = box.left * width
            val top = box.top * height
            val right = box.right * width
            val bottom = box.bottom * height

            canvas.drawRect(left, top, right, bottom, paint)
        }
    }
}
