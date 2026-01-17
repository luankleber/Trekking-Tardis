package com.example.yoloconedetector

import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.yolo.OverlayView
import com.example.yolo.YOLODetector
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var yoloDetector: YOLODetector

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)

        yoloDetector = YOLODetector(
            context = this,
            modelPath = "best_modelo.tflite",
            inputSize = 320
        )

        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()

            analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                val bitmap = imageProxy.toBitmapCorrected()
                val results = yoloDetector.detect(bitmap)

                runOnUiThread {
                    overlayView.setResults(results)
                }

                imageProxy.close()
            }

            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                this,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                analysis
            )

        }, ContextCompat.getMainExecutor(this))
    }
}

/* =========================
   EXTENSÕES AUXILIARES
   ========================= */

/**
 * Converte ImageProxy para Bitmap e aplica a rotação correta
 * baseada no rotationDegrees do CameraX
 */
private fun ImageProxy.toBitmapCorrected(): Bitmap {
    val bitmap = this.toBitmap()
    return bitmap.rotate(imageInfo.rotationDegrees.toFloat())
}

/**
 * Rotaciona o bitmap corretamente (corrige X/Y invertidos)
 */
private fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
