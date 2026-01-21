package com.example.yoloconedetector

import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.util.Log
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

enum class NavState {
    SEARCH_CONE,
    ALIGN_TO_CONE,
    APPROACH_CONE
}

data class DriveCommand(
    val steering: Float,
    val throttle: Float
)

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var overlayView: OverlayView
    private lateinit var debugAngleView: DebugAngleView

    private lateinit var yoloDetector: YOLODetector
    private lateinit var imuManager: ImuManager

    private lateinit var bluetoothController: BluetoothController
    @Volatile private var bluetoothConnected = false

    private val cameraExecutor = Executors.newSingleThreadExecutor()

    private var lastFrameTimestampNs: Long = 0L
    private val cameraFovRad = Math.toRadians(70.0).toFloat()

    private var lastValidDetections: List<YoloDetection> = emptyList()

    private val kpAngular = 1.2f
    private var navState = NavState.SEARCH_CONE

    private val REQ_BT = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.e("LIFE", "MainActivity onCreate")

        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        overlayView = findViewById(R.id.overlayView)
        debugAngleView = findViewById(R.id.debugAngleView)

        yoloDetector = YOLODetector(
            context = this,
            modelPath = "best_modelo.tflite",
            inputSize = 320
        )

        imuManager = ImuManager(this)
        imuManager.start()

        bluetoothController = BluetoothController(this)

        requestBluetoothIfNeeded()
        startCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        imuManager.stop()
        cameraExecutor.shutdown()
    }

    /* =========================
       BLUETOOTH
       ========================= */

    private fun requestBluetoothIfNeeded() {
        if (android.os.Build.VERSION.SDK_INT >= 31) {

            val missing = mutableListOf<String>()

            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missing += android.Manifest.permission.BLUETOOTH_CONNECT
            }

            if (checkSelfPermission(android.Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED
            ) {
                missing += android.Manifest.permission.BLUETOOTH_SCAN
            }

            if (missing.isNotEmpty()) {
                requestPermissions(missing.toTypedArray(), REQ_BT)
            } else {
                startBluetooth()
            }

        } else {
            startBluetooth()
        }
    }


    private fun startBluetooth() {
        Thread {
            Log.e("BT_FLOW", "Bluetooth thread started")

            bluetoothConnected =
                bluetoothController.connect("TARDIS_ROBOT")

            Log.e("BT_FLOW", "Bluetooth connected = $bluetoothConnected")
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQ_BT &&
            grantResults.isNotEmpty() &&
            grantResults[0] == PackageManager.PERMISSION_GRANTED
        ) {
            startBluetooth()
        }
    }

    /* =========================
       CÂMERA
       ========================= */

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

                val frameTimestamp = imageProxy.imageInfo.timestamp
                val yawRate = imuManager.yawRate

                val deltaYawRad =
                    if (lastFrameTimestampNs != 0L) {
                        val dt = (frameTimestamp - lastFrameTimestampNs) * 1e-9f
                        yawRate * dt
                    } else 0f

                lastFrameTimestampNs = frameTimestamp

                val bitmap = imageProxy.toBitmapCorrected()
                val imageWidth = bitmap.width

                val pixelShift =
                    (deltaYawRad / cameraFovRad) * imageWidth

                val rawResults = yoloDetector.detect(bitmap)

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

                val filteredResults = predictedResults.filter { det ->
                    val previous = lastValidDetections.firstOrNull {
                        it.classId == det.classId
                    } ?: return@filter true

                    val prevCx =
                        (previous.boundingBox.left + previous.boundingBox.right) * 0.5f
                    val currCx =
                        (det.boundingBox.left + det.boundingBox.right) * 0.5f

                    abs(currCx - prevCx) * imageWidth < 60f
                }

                lastValidDetections = filteredResults

                val coneAngleRad =
                    filteredResults.firstOrNull()?.let { det ->
                        val box = det.boundingBox
                        val cxNorm = (box.left + box.right) * 0.5f
                        (cxNorm - 0.5f) * cameraFovRad
                    }

                updateNavigation(coneAngleRad)
                val driveCommand = computeDriveCommand(coneAngleRad)

                if (bluetoothConnected) {
                    Log.i(
                        "BT_FLOW",
                        "Sending steer=${driveCommand.steering}, throttle=${driveCommand.throttle}"
                    )

                    bluetoothController.send(
                        driveCommand.steering,
                        driveCommand.throttle
                    )
                }

                runOnUiThread {
                    overlayView.setResults(filteredResults)
                    debugAngleView.setAngle(coneAngleRad ?: 0f)
                    debugAngleView.setState(navState.name)
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

    /* =========================
       ESTADOS (INTOCADO)
       ========================= */

    private fun updateNavigation(coneAngleRad: Float?) {
        navState = when (navState) {

            NavState.SEARCH_CONE ->
                if (coneAngleRad != null) NavState.ALIGN_TO_CONE else navState

            NavState.ALIGN_TO_CONE ->
                when {
                    coneAngleRad == null -> NavState.SEARCH_CONE
                    abs(coneAngleRad) < Math.toRadians(3.0) -> NavState.APPROACH_CONE
                    else -> navState
                }

            NavState.APPROACH_CONE ->
                if (coneAngleRad == null) NavState.SEARCH_CONE else navState
        }
    }

    private fun computeDriveCommand(coneAngleRad: Float?): DriveCommand =
        when (navState) {

            NavState.SEARCH_CONE ->
                DriveCommand(steering = 0.35f, throttle = 0.2f)

            NavState.ALIGN_TO_CONE ->
                DriveCommand(
                    steering = (-kpAngular * (coneAngleRad ?: 0f))
                        .coerceIn(-1f, 1f),
                    throttle = 0.15f
                )

            NavState.APPROACH_CONE ->
                DriveCommand(
                    steering = (-kpAngular * (coneAngleRad ?: 0f))
                        .coerceIn(-1f, 1f),
                    throttle = 0.35f
                )
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
