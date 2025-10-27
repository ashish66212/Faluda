package com.chesschat.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View

class BoardGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val gridPaint = Paint().apply {
        color = Color.parseColor("#4CAF50")
        style = Paint.Style.STROKE
        strokeWidth = 2f
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.parseColor("#1976D2")
        textSize = 32f
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
        isFakeBoldText = true
    }

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#E8F5E9")
        style = Paint.Style.FILL
    }

    private val highlightPaint = Paint().apply {
        color = Color.parseColor("#C8E6C9")
        style = Paint.Style.FILL
    }

    private val files = listOf('a', 'b', 'c', 'd', 'e', 'f', 'g', 'h')
    private val ranks = listOf('1', '2', '3', '4', '5', '6', '7', '8')

    var isFlipped = false
        set(value) {
            field = value
            invalidate()
        }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val width = width.toFloat()
        val height = height.toFloat()

        if (width == 0f || height == 0f) return

        val squareWidth = width / 8f
        val squareHeight = height / 8f

        // FIXED: File outer loop, Rank inner loop for proper chess board mapping
        for (file in 0..7) {
            for (rank in 0..7) {
                val left = file * squareWidth
                val top = rank * squareHeight
                val right = left + squareWidth
                val bottom = top + squareHeight

                val rect = RectF(left, top, right, bottom)
                
                // Chess board coloring (light and dark squares)
                if ((file + rank) % 2 == 0) {
                    canvas.drawRect(rect, backgroundPaint)
                } else {
                    canvas.drawRect(rect, highlightPaint)
                }

                // Draw grid border
                canvas.drawRect(rect, gridPaint)

                // CORRECTED: Coordinate calculation for both orientations
                val displayFile = if (isFlipped) 7 - file else file
                val displayRank = if (isFlipped) rank else 7 - rank

                val fileChar = files[displayFile]
                val rankChar = ranks[displayRank]
                val uciLabel = "$fileChar$rankChar"

                // Text positioning
                val centerX = left + squareWidth / 2f
                val centerY = top + squareHeight / 2f + (textPaint.textSize / 3f)

                canvas.drawText(uciLabel, centerX, centerY, textPaint)
            }
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = Math.min(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }
}