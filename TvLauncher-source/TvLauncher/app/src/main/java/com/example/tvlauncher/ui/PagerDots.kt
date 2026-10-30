package com.example.tvlauncher.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

/** Indicador do destaque: uma pílula para o item atual e pontos para os demais. */
class PagerDots @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val dot = 4 * density
    private val gap = 4 * density
    private val pill = 20 * density
    private val rect = RectF()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }

    var count = 0
        set(value) {
            if (field == value) return
            field = value
            requestLayout()
            invalidate()
        }

    var index = 0
        set(value) {
            if (field == value) return
            field = value
            invalidate()
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = if (count == 0) 0f else pill + (count - 1) * (dot + gap)
        setMeasuredDimension(
            resolveSize(width.toInt(), widthMeasureSpec),
            resolveSize(dot.toInt(), heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        var x = 0f
        for (i in 0 until count) {
            val active = i == index
            val w = if (active) pill else dot
            paint.alpha = if (active) 255 else 115
            rect.set(x, 0f, x + w, dot)
            canvas.drawRoundRect(rect, dot / 2, dot / 2, paint)
            x += w + gap
        }
    }
}
