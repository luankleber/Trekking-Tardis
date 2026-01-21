package com.example.yoloconedetector

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class DebugAngleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    // Ângulo atual (rad)
    private var angleRad: Float = 0f

    // Estado atual do robô
    private var stateText: String = "INIT"

    /* ===============================
       PAINTS
       =============================== */

    private val centerPaint = Paint().apply {
        color = Color.rgb(0, 255, 0)
        strokeWidth = 10f
        isAntiAlias = true
    }

    private val barPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    private val textStrokePaint = Paint().apply {
        color = Color.BLACK
        letterSpacing = 0.1f
        textSize = 36f
        style = Paint.Style.STROKE
        strokeWidth = 4f
        isAntiAlias = true
        typeface = resources.getFont(R.font.sharetechmono_regular)
    }

    private val textFillPaint = Paint().apply {
        color = Color.GREEN
        letterSpacing = 0.1f
        textSize = 36f
        style = Paint.Style.FILL
        isAntiAlias = true
        typeface = resources.getFont(R.font.sharetechmono_regular)
    }

    private val deadZonePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 8f
        isAntiAlias = true
    }

    /* ===============================
       API PÚBLICA
       =============================== */

    fun setAngle(angle: Float) {
        angleRad = angle
        invalidate()
    }

    fun setState(state: String) {
        stateText = state
        invalidate()
    }

    /* ===============================
       DESENHO
       =============================== */

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        val cx = w * 0.5f
        val cy = h * 0.15f

        /* -------- Linha central -------- */
        canvas.drawLine(
            cx,
            cy - 40f,
            cx,
            cy + 40f,
            centerPaint
        )

        /* -------- Texto YAW -------- */
        val deg = Math.toDegrees(angleRad.toDouble())
        val yawText = String.format("TARGET YAW %.1f°", deg)

        canvas.drawText(yawText, cx - 170f, cy - 60f, textStrokePaint)
        canvas.drawText(yawText, cx - 170f, cy - 60f, textFillPaint)

        /* -------- Texto STATE -------- */
        val stateY = cy + 100f

        canvas.drawText(
            "STATE: $stateText",
            cx - 220f,
            stateY,
            textStrokePaint
        )

        canvas.drawText(
            "STATE: $stateText",
            cx - 220f,
            stateY,
            textFillPaint
        )

        /* -------- Normalização -------- */
        val maxAngle = Math.toRadians(35.0).toFloat()
        var norm = angleRad / maxAngle
        norm = max(-1f, min(1f, norm))

        /* -------- Dead zone -------- */
        val deadZoneRad = Math.toRadians(4.0).toFloat()
        val deadZonePx = (deadZoneRad / maxAngle) * (w * 0.4f)

        canvas.drawLine(
            cx - deadZonePx,
            cy,
            cx + deadZonePx,
            cy,
            deadZonePaint
        )

        /* -------- Cor da barra -------- */
        barPaint.color = when {
            abs(angleRad) < deadZoneRad -> Color.GREEN
            angleRad > 0f -> Color.RED
            else -> Color.YELLOW
        }

        /* -------- Barra -------- */
        val barLength = abs(norm) * (w * 0.4f)
        val endX = if (norm >= 0f) cx + barLength else cx - barLength

        canvas.drawLine(cx, cy, endX, cy, barPaint)
    }
}
