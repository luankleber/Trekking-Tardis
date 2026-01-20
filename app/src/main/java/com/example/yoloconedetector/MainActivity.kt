package com.example.yoloconedetector

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import com.example.yolo.OverlayView
import com.example.yolo.YOLODetector
import com.example.yolo.YoloDetection
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var yoloDetector: YOLODetector
    private lateinit var imuManager: ImuManager

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var lastFrameTimestampNs: Long = 0L
    private val cameraFovRad = Math.toRadians(70.0).toFloat()

    private var lastValidDetections: List<YoloDetection> = emptyList()

    // ganho do controlador angular
    private val kpAngular = 1.2f

    private lateinit var debugAngleView: DebugAngleView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        debugAngleView = findViewById(R.id.debugAngleView)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)

        yoloDetector = YOLODetector(
            context = this,
            modelPath = "best_modelo.tflite",
            inputSize = 320
        )

        imuManager = ImuManager(this)
        imuManager.start()

        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        imuManager.stop()
        cameraExecutor.shutdown()
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

                /* =========================
                   IMU → Δyaw
                   ========================= */

                val frameTimestamp = imageProxy.imageInfo.timestamp
                val yawRate = imuManager.yawRate

                val deltaYawRad =
                    if (lastFrameTimestampNs != 0L) {
                        val dt = (frameTimestamp - lastFrameTimestampNs) * 1e-9f
                        yawRate * dt
                    } else {
                        0f
                    }

                lastFrameTimestampNs = frameTimestamp

                /* =========================
                   IMAGEM
                   ========================= */

                val bitmap = imageProxy.toBitmapCorrected()
                val imageWidth = bitmap.width

                val pixelShift =
                    (deltaYawRad / cameraFovRad) * imageWidth

                /* =========================
                   YOLO
                   ========================= */

                val rawResults = yoloDetector.detect(bitmap)

                // 1️⃣ Compensação angular
                val predictedResults = rawResults.map { det ->
                    val box = det.boundingBox
                    det.copy(
                        boundingBox = RectF(
                            box.left - pixelShift / imageWidth,
                            box.top,
                            box.right - pixelShift / imageWidth,
                            box.bottom
                        )
                    )
                }

                // 2️⃣ Filtro temporal simples
                val filteredResults = predictedResults.filter { det ->

                    val previous = lastValidDetections.firstOrNull {
                        it.classId == det.classId
                    } ?: return@filter true

                    val prevCx =
                        (previous.boundingBox.left + previous.boundingBox.right) * 0.5f
                    val currCx =
                        (det.boundingBox.left + det.boundingBox.right) * 0.5f

                    val errorPx = abs(currCx - prevCx) * imageWidth
                    errorPx < 60f
                }

                lastValidDetections = filteredResults

                /* =========================
                   CONTROLE ANGULAR
                   ========================= */

                if (filteredResults.isNotEmpty()) {

                    val target = filteredResults[0]
                    val box = target.boundingBox

                    // centro normalizado do cone (0..1)
                    val cxNorm = (box.left + box.right) * 0.5f

                    // ângulo do cone em relação à câmera
                    val coneAngleRad =
                        (cxNorm - 0.5f) * cameraFovRad

                    // controlador proporcional
                    val angularCommand =
                        -kpAngular * coneAngleRad

                    runOnUiThread {
                        debugAngleView.setAngle(coneAngleRad)
                    }

                    android.util.Log.d(
                        "CONTROL",
                        "coneAngle=$coneAngleRad rad | cmd=$angularCommand"
                    )
                }

                runOnUiThread {
                    overlayView.setResults(filteredResults)
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
   EXTENSÕES
   ========================= */

private fun ImageProxy.toBitmapCorrected(): Bitmap {
    val bitmap = this.toBitmap()
    return bitmap.rotate(imageInfo.rotationDegrees.toFloat())
}

private fun Bitmap.rotate(degrees: Float): Bitmap {
    if (degrees == 0f) return this
    val matrix = Matrix().apply { postRotate(degrees) }
    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}
