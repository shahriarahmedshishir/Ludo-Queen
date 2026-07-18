package com.example.ludoqueen

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class DiceView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var value: Int = 0
        set(v) { field = v; invalidate() }

    var accentColor: Int = Color.parseColor("#E53935")
        set(v) { field = v; invalidate() }

    var isActive: Boolean = false
        set(v) { field = v; invalidate() }

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val pipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.parseColor("#222222") }

    private val pipLayouts: Map<Int, List<Pair<Float, Float>>> = mapOf(
        1 to listOf(0.5f to 0.5f),
        2 to listOf(0.25f to 0.25f, 0.75f to 0.75f),
        3 to listOf(0.25f to 0.25f, 0.5f to 0.5f, 0.75f to 0.75f),
        4 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.25f to 0.75f, 0.75f to 0.75f),
        5 to listOf(0.25f to 0.25f, 0.75f to 0.25f, 0.5f to 0.5f, 0.25f to 0.75f, 0.75f to 0.75f),
        6 to listOf(0.25f to 0.2f, 0.75f to 0.2f, 0.25f to 0.5f, 0.75f to 0.5f, 0.25f to 0.8f, 0.75f to 0.8f)
    )

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val size = minOf(width, height).toFloat()
        val corner = size * 0.18f
        val rect = RectF(2f, 2f, size - 2f, size - 2f)

        bgPaint.color = Color.WHITE
        canvas.drawRoundRect(rect, corner, corner, bgPaint)

        borderPaint.color = if (isActive) accentColor else Color.parseColor("#888888")
        borderPaint.strokeWidth = if (isActive) size * 0.09f else size * 0.04f
        canvas.drawRoundRect(rect, corner, corner, borderPaint)

        val pips = pipLayouts[value] ?: return
        val pipRadius = size * 0.09f
        pips.forEach { (fx, fy) ->
            canvas.drawCircle(size * fx, size * fy, pipRadius, pipPaint)
        }

        alpha = if (isActive) 1.0f else 0.45f
    }
}