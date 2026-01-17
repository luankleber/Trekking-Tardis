package com.example.yolo

import android.graphics.RectF

data class YoloDetection(
    val boundingBox: RectF,
    val score: Float,
    val classId: Int
)
