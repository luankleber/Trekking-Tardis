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

    // Linha vertical central (referência zero)
    private val centerPaint = Paint().apply {
        color = Color.rgb(0, 255, 0)
        strokeWidth = 10f
        isAntiAlias = true
    }

    // Barra horizontal de erro (yaw)
    private val barPaint = Paint().apply {
        color = Color.GREEN
        strokeWidth = 14f
        strokeCap = Paint.Cap.ROUND
        isAntiAlias = true
    }

    // Texto HUD - CONTORNO
    private val textStrokePaint = Paint().apply {
        color = Color.BLACK
        letterSpacing = 0.1f
        textSize = 36f
        style = Paint.Style.STROKE
        strokeWidth = 4f        // controla a “grossura” do contorno
        isAntiAlias = true
        typeface = resources.getFont(R.font.sharetechmono_regular)
    }

    // Texto HUD - PREENCHIMENTO
    private val textFillPaint = Paint().apply {
        color = Color.GREEN
        letterSpacing = 0.1f
        textSize = 36f
        style = Paint.Style.FILL
        isAntiAlias = true
        typeface = resources.getFont(R.font.sharetechmono_regular)
    }

    // Linha de dead-zone (opcional)
    private val deadZonePaint = Paint().apply {
        color = Color.GRAY
        strokeWidth = 8f
        isAntiAlias = true
    }

    fun setAngle(angle: Float) {
        angleRad = angle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val w = width.toFloat()
        val h = height.toFloat()

        // Centro da HUD
        val cx = w * 0.5f
        val cy = h * 0.15f   // HUD um pouco abaixo do centro

        /* ===============================
           1) LINHA CENTRAL (referência)
           =============================== */
        canvas.drawLine(
            cx,
            cy - 40f,
            cx,
            cy + 40f,
            centerPaint
        )

        /* ===============================
           2) TEXTO HUD
           =============================== */
        val deg = Math.toDegrees(angleRad.toDouble())
        val text = String.format("TARGET YAW %.1f°", deg)

// contorno
        canvas.drawText(
            text,
            cx - 170f,
            cy - 60f,
            textStrokePaint
        )

// preenchimento
        canvas.drawText(
            text,
            cx - 170f,
            cy - 60f,
            textFillPaint
        )

        /* ===============================
           3) NORMALIZAÇÃO DO ÂNGULO
           =============================== */

        // Ângulo máximo visual (ex: metade do FOV)
        val maxAngle = Math.toRadians(35.0).toFloat()

        var norm = angleRad / maxAngle
        norm = max(-1f, min(1f, norm))

        /* ===============================
           4) DEAD ZONE VISUAL (±2°)
           =============================== */
        val deadZoneRad = Math.toRadians(4.0).toFloat()
        val deadZonePx = (deadZoneRad / maxAngle) * (w * 0.4f)

        canvas.drawLine(
            cx - deadZonePx,
            cy,
            cx + deadZonePx,
            cy,
            deadZonePaint
        )

        /* ===============================
           5) COR DINÂMICA DA BARRA
           =============================== */
        barPaint.color = when {
            abs(angleRad) < deadZoneRad -> Color.GREEN   // alinhado
            angleRad > 0f -> Color.RED                  // erro à direita
            else -> Color.YELLOW                        // erro à esquerda
        }

        /* ===============================
           6) BARRA DE ERRO (yaw)
           =============================== */
        val barLength = abs(norm) * (w * 0.4f)

        val endX =
            if (norm >= 0f) cx + barLength
            else cx - barLength

        canvas.drawLine(
            cx,
            cy,
            endX,
            cy,
            barPaint
        )
    }
}
