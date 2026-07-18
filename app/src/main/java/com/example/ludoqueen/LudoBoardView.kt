package com.example.ludoqueen

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class LudoBoardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        // Single source of truth for where yard slots sit inside a home quadrant.
        // Both the decorative slot outline AND the real token position use this.
        private const val YARD_INNER_MARGIN_FRACTION = 0.20f
    }

    private val gridSize = 15
    private var cellSize = 0f

    private val colorRed = Color.parseColor("#C62828")
    private val colorGreen = Color.parseColor("#2E7D32")
    private val colorBlue = Color.parseColor("#1565C0")
    private val colorYellow = Color.parseColor("#FBC02D")
    private val colorWhite = Color.WHITE
    private val colorGridLine = Color.parseColor("#B0BEC5")
    private val colorStar = Color.parseColor("#78909C")

    private val colorPanelBg = Color.parseColor("#ECEFF1")
    private val colorPanelBorder = Color.parseColor("#FBC02D")

    private fun playerColor(playerIndex: Int) = when (playerIndex) {
        0 -> colorRed; 1 -> colorGreen; 2 -> colorYellow; else -> colorBlue
    }

    var activePlayerIndex: Int = 0
        set(value) { field = value; invalidate() }

    var selectableTokens: Set<Pair<Int, Int>> = emptySet()
        set(value) { field = value; invalidate() }

    var onTokenTapped: ((Int, Int) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = colorGridLine
    }
    private val tokenStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f; color = Color.WHITE
    }
    private val tokenDarkOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 3f; color = Color.parseColor("#212121")
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 8f; color = Color.parseColor("#4CAF50")
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL; color = colorStar
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 6f; color = Color.parseColor("#FFEB3B")
    }
    private val slotOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5f
    }

    private val tokenPixelPositions = HashMap<String, PointF>()
    private var engine: LudoEngine? = null

    private fun key(p: Int, t: Int) = "${p}_$t"

    /** Recomputes cellSize from current view dimensions. Safe to call multiple times. */
    private fun updateCellSize() {
        val boardSize = minOf(width, height).toFloat()
        if (boardSize > 0f) {
            cellSize = boardSize / gridSize
        }
    }

    fun bindEngine(engine: LudoEngine) {
        this.engine = engine
        updateCellSize()
        snapAllTokensToCurrentState()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCellSize()
        snapAllTokensToCurrentState()
    }

    fun snapAllTokensToCurrentState() {
        if (cellSize == 0f) return
        val e = engine ?: return
        e.players.forEach { player ->
            player.tokens.forEach { token ->
                tokenPixelPositions[key(token.playerIndex, token.tokenIndex)] =
                    pixelForToken(token.playerIndex, token.tokenIndex, token.localPosition)
            }
        }
        invalidate()
    }

    private fun pixelForToken(playerIndex: Int, tokenIndex: Int, localPosition: Int): PointF {
        return if (localPosition == -1) {
            yardSlotCenter(playerIndex, tokenIndex)
        } else {
            val cell = LudoPathData.localPositionToCell(playerIndex, localPosition)
            cellCenter(cell.first, cell.second)
        }
    }

    private fun cellCenter(row: Int, col: Int) =
        PointF(col * cellSize + cellSize / 2f, row * cellSize + cellSize / 2f)

    private fun yardSlotCenter(playerIndex: Int, tokenIndex: Int): PointF {
        val (startRow, startCol) = when (playerIndex) {
            0 -> 0 to 0
            1 -> 0 to 9
            2 -> 9 to 9
            else -> 9 to 0
        }
        val left = startCol * cellSize
        val top = startRow * cellSize
        val size = cellSize * 6
        val innerMargin = size * YARD_INNER_MARGIN_FRACTION
        val innerLeft = left + innerMargin
        val innerTop = top + innerMargin
        val innerSize = size - innerMargin * 2
        val (fx, fy) = LudoPathData.yardSlotFraction(tokenIndex)
        return PointF(innerLeft + innerSize * fx, innerTop + innerSize * fy)
    }

    fun animateTokenMove(playerIndex: Int, tokenIndex: Int, fromLocal: Int, toLocal: Int, stepDurationMs: Long = 220L, onComplete: () -> Unit) {
        val waypoints = mutableListOf<PointF>()
        if (fromLocal == -1) {
            waypoints.add(yardSlotCenter(playerIndex, tokenIndex))
            waypoints.add(pixelForToken(playerIndex, tokenIndex, 0))
            for (pos in 1..toLocal) {
                waypoints.add(pixelForToken(playerIndex, tokenIndex, pos))
            }
        } else {
            for (pos in fromLocal..toLocal) {
                waypoints.add(pixelForToken(playerIndex, tokenIndex, pos))
            }
        }
        animateWaypoints(key(playerIndex, tokenIndex), waypoints, stepDurationMs, onComplete)
    }

    fun snapTokenToYard(playerIndex: Int, tokenIndex: Int) {
        tokenPixelPositions[key(playerIndex, tokenIndex)] = yardSlotCenter(playerIndex, tokenIndex)
        invalidate()
    }

    private fun animateWaypoints(tokenKey: String, waypoints: List<PointF>, stepDurationMs: Long, onComplete: () -> Unit) {
        if (waypoints.size < 2) { onComplete(); return }
        var index = 0

        fun animateStep() {
            if (index >= waypoints.size - 1) { onComplete(); return }
            val start = waypoints[index]
            val end = waypoints[index + 1]
            val animator = ValueAnimator.ofFloat(0f, 1f)
            animator.duration = stepDurationMs
            animator.interpolator = LinearInterpolator()
            animator.addUpdateListener { anim ->
                val frac = anim.animatedValue as Float
                tokenPixelPositions[tokenKey] = PointF(
                    start.x + (end.x - start.x) * frac,
                    start.y + (end.y - start.y) * frac
                )
                invalidate()
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    index++
                    animateStep()
                }
            })
            animator.start()
        }
        animateStep()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN && selectableTokens.isNotEmpty()) {
            val touchX = event.x
            val touchY = event.y
            var closestKey: Pair<Int, Int>? = null
            var closestDist = Float.MAX_VALUE

            selectableTokens.forEach { (p, t) ->
                val pos = tokenPixelPositions[key(p, t)] ?: return@forEach
                val dx = pos.x - touchX
                val dy = pos.y - touchY
                val dist = dx * dx + dy * dy
                if (dist < closestDist) {
                    closestDist = dist
                    closestKey = p to t
                }
            }

            val threshold = cellSize * 1.2f
            if (closestKey != null && closestDist <= threshold * threshold) {
                onTokenTapped?.invoke(closestKey!!.first, closestKey!!.second)
                return true
            }
        }
        return false
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        updateCellSize()

        val boardSize = minOf(width, height).toFloat()

        fillPaint.color = colorWhite
        canvas.drawRect(0f, 0f, boardSize, boardSize, fillPaint)

        drawHomeQuadrant(canvas, 0, 0, colorRed, 0)
        drawHomeQuadrant(canvas, 0, 9, colorGreen, 1)
        drawHomeQuadrant(canvas, 9, 9, colorYellow, 2)
        drawHomeQuadrant(canvas, 9, 0, colorBlue, 3)

        drawPathGrid(canvas)
        drawSafeStars(canvas)
        drawHomeStretches(canvas)
        drawCenterTriangles(canvas)
        drawLudoKingProfiles(canvas)
        drawTokens(canvas)
    }

    private fun drawLudoKingProfiles(canvas: Canvas) {
        textPaint.textSize = cellSize * 0.45f

        val p1Name = engine?.players?.getOrNull(0)?.label ?: "Player 1"
        val p2Name = engine?.players?.getOrNull(1)?.label ?: "Player 2"
        val p3Name = engine?.players?.getOrNull(2)?.label ?: "Player 3"
        val p4Name = engine?.players?.getOrNull(3)?.label ?: "Player 4"

        drawProfileBox(canvas, 0, cellSize * 0.2f, cellSize * 0.2f, activePlayerIndex == 0)
        textPaint.color = Color.WHITE
        canvas.drawText(p1Name, cellSize * 3f, cellSize * 0.6f, textPaint)

        drawProfileBox(canvas, 1, cellSize * 13.3f, cellSize * 0.2f, activePlayerIndex == 1)
        canvas.drawText(p2Name, cellSize * 12f, cellSize * 0.6f, textPaint)

        drawProfileBox(canvas, 2, cellSize * 13.3f, cellSize * 13.8f, activePlayerIndex == 2)
        canvas.drawText(p3Name, cellSize * 12f, cellSize * 14.6f, textPaint)

        drawProfileBox(canvas, 3, cellSize * 0.2f, cellSize * 13.8f, activePlayerIndex == 3)
        canvas.drawText(p4Name, cellSize * 3f, cellSize * 14.6f, textPaint)
    }

    private fun drawProfileBox(canvas: Canvas, playerIndex: Int, x: Float, y: Float, isActive: Boolean) {
        val width = cellSize * 1.5f
        val height = cellSize * 1.0f
        val rect = RectF(x, y, x + width, y + height)

        fillPaint.color = colorPanelBg
        canvas.drawRoundRect(rect, 12f, 12f, fillPaint)

        strokePaint.color = colorPanelBorder
        strokePaint.strokeWidth = 4f
        canvas.drawRoundRect(rect, 12f, 12f, strokePaint)

        val avatarRect = RectF(x + 6f, y + 6f, x + (width * 0.35f), y + height - 6f)
        fillPaint.color = playerColor(playerIndex)
        canvas.drawRoundRect(avatarRect, 6f, 6f, fillPaint)

        fillPaint.color = Color.WHITE
        canvas.drawCircle(avatarRect.centerX(), avatarRect.centerY(), avatarRect.width() * 0.25f, fillPaint)

        if (isActive) {
            canvas.drawRoundRect(rect, 12f, 12f, highlightPaint)
            drawTurnArrow(canvas, x, y, playerIndex)
        }
    }

    private fun drawTurnArrow(canvas: Canvas, x: Float, y: Float, playerIndex: Int) {
        fillPaint.color = Color.parseColor("#FFD54F")
        val path = Path()
        val arrowSize = cellSize * 0.35f

        when (playerIndex) {
            0, 3 -> {
                val startX = x + (cellSize * 1.6f)
                val startY = y + (cellSize * 0.5f)
                path.moveTo(startX, startY)
                path.lineTo(startX + arrowSize, startY - (arrowSize / 1.5f))
                path.lineTo(startX + arrowSize, startY + (arrowSize / 1.5f))
            }
            1, 2 -> {
                val startX = x - (cellSize * 0.1f)
                val startY = y + (cellSize * 0.5f)
                path.moveTo(startX, startY)
                path.lineTo(startX - arrowSize, startY - (arrowSize / 1.5f))
                path.lineTo(startX - arrowSize, startY + (arrowSize / 1.5f))
            }
        }
        path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawSafeStars(canvas: Canvas) {
        LudoPathData.SAFE_SQUARES.forEach { globalIndex ->
            val cell = LudoPathData.RING_PATH[globalIndex]
            val center = cellCenter(cell.first, cell.second)
            drawStar(canvas, center.x, center.y, cellSize * 0.35f, cellSize * 0.15f)
        }
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, outerR: Float, innerR: Float) {
        val path = Path()
        val points = 5
        for (i in 0 until points * 2) {
            val radius = if (i % 2 == 0) outerR else innerR
            val angle = (PI / points) * i - PI / 2
            val x = cx + (radius * cos(angle)).toFloat()
            val y = cy + (radius * sin(angle)).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        canvas.drawPath(path, starPaint)
    }

    private fun drawTokens(canvas: Canvas) {
        val e = engine ?: return
        val radius = cellSize * 0.35f

        val trackGroups = HashMap<String, MutableList<Pair<Int, Int>>>()
        val yardPieces = mutableListOf<Pair<Int, Int>>()

        e.players.forEach { player ->
            player.tokens.forEach { token ->
                // Self-heal: if this token has no cached pixel position yet
                // (can happen if bindEngine ran before layout finished),
                // compute it live right now instead of skipping the draw.
                val cacheKey = key(token.playerIndex, token.tokenIndex)
                if (tokenPixelPositions[cacheKey] == null) {
                    tokenPixelPositions[cacheKey] =
                        pixelForToken(token.playerIndex, token.tokenIndex, token.localPosition)
                }

                if (token.localPosition == -1) {
                    yardPieces.add(token.playerIndex to token.tokenIndex)
                } else {
                    val cell = LudoPathData.localPositionToCell(token.playerIndex, token.localPosition)
                    val cellKey = "${cell.first}_${cell.second}"
                    trackGroups.getOrPut(cellKey) { mutableListOf() }.add(token.playerIndex to token.tokenIndex)
                }
            }
        }

        yardPieces.forEach { (pIdx, tIdx) ->
            val base = tokenPixelPositions[key(pIdx, tIdx)]
                ?: pixelForToken(pIdx, tIdx, -1)
            drawSingleToken(canvas, base.x, base.y, radius, pIdx, tIdx)
        }

        trackGroups.values.forEach { stack ->
            stack.forEachIndexed { i, (pIdx, tIdx) ->
                val base = tokenPixelPositions[key(pIdx, tIdx)]
                    ?: pixelForToken(pIdx, tIdx, e.players[pIdx].tokens[tIdx].localPosition)
                val offset = if (stack.size > 1) (i - (stack.size - 1) / 2f) * (radius * 0.5f) else 0f

                val targetX = base.x + offset
                val targetY = base.y

                drawSingleToken(canvas, targetX, targetY, radius, pIdx, tIdx)
            }
        }
    }

    private fun drawSingleToken(canvas: Canvas, targetX: Float, targetY: Float, radius: Float, pIdx: Int, tIdx: Int) {
        fillPaint.color = playerColor(pIdx)
        canvas.drawCircle(targetX, targetY, radius, fillPaint)

        canvas.drawCircle(targetX, targetY, radius, tokenStrokePaint)

        canvas.drawCircle(targetX, targetY, radius * 0.45f, tokenDarkOutlinePaint)

        fillPaint.color = Color.WHITE
        canvas.drawCircle(targetX, targetY, radius * 0.25f, fillPaint)

        if ((pIdx to tIdx) in selectableTokens) {
            canvas.drawCircle(targetX, targetY, radius + 8f, selectPaint)
        }
    }

    private fun cellLeft(col: Int) = col * cellSize
    private fun cellTop(row: Int) = row * cellSize

    private fun drawHomeQuadrant(canvas: Canvas, startRow: Int, startCol: Int, color: Int, playerIndex: Int) {
        val left = cellLeft(startCol)
        val top = cellTop(startRow)
        val size = cellSize * 6

        fillPaint.color = color
        canvas.drawRect(left, top, left + size, top + size, fillPaint)

        val innerMargin = size * YARD_INNER_MARGIN_FRACTION
        val innerLeft = left + innerMargin
        val innerTop = top + innerMargin
        val innerSize = size - innerMargin * 2
        fillPaint.color = colorWhite
        val innerRect = RectF(innerLeft, innerTop, innerLeft + innerSize, innerTop + innerSize)
        canvas.drawRoundRect(innerRect, 16f, 16f, fillPaint)

        // Empty-slot outlines only — NOT filled. The real, solid token (drawSingleToken)
        // renders on top of whichever slot currently holds a piece. An outline with no
        // solid circle inside it means that token has left the yard.
        slotOutlinePaint.color = color
        for (i in 0..3) {
            val (fx, fy) = LudoPathData.yardSlotFraction(i)
            val socketX = innerLeft + innerSize * fx
            val socketY = innerTop + innerSize * fy
            canvas.drawCircle(socketX, socketY, cellSize * 0.38f, slotOutlinePaint)
        }
    }

    private fun drawPathGrid(canvas: Canvas) {
        strokePaint.color = colorGridLine
        strokePaint.strokeWidth = 2f
        for (row in 0 until gridSize) {
            for (col in 0 until gridSize) {
                val inTL = row in 0..5 && col in 0..5
                val inTR = row in 0..5 && col in 9..14
                val inBL = row in 9..14 && col in 0..5
                val inBR = row in 9..14 && col in 9..14
                val inCenter = row in 6..8 && col in 6..8
                if (inTL || inTR || inBL || inBR || inCenter) continue
                val left = cellLeft(col); val top = cellTop(row)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, strokePaint)
            }
        }
    }

    private fun drawHomeStretches(canvas: Canvas) {
        drawStrip(canvas, colorRed, row = 7, colRange = 1..5)
        drawStrip(canvas, colorGreen, col = 7, rowRange = 1..5)
        drawStrip(canvas, colorYellow, row = 7, colRange = 9..13)
        drawStrip(canvas, colorBlue, col = 7, rowRange = 9..13)
    }

    private fun drawStrip(canvas: Canvas, color: Int, row: Int = -1, col: Int = -1, rowRange: IntRange? = null, colRange: IntRange? = null) {
        fillPaint.color = color
        if (colRange != null) {
            for (c in colRange) {
                val left = cellLeft(c); val top = cellTop(row)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, strokePaint)
            }
        }
        if (rowRange != null) {
            for (r in rowRange) {
                val left = cellLeft(col); val top = cellTop(r)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, fillPaint)
                canvas.drawRect(left, top, left + cellSize, top + cellSize, strokePaint)
            }
        }
    }

    private fun drawCenterTriangles(canvas: Canvas) {
        val centerLeft = cellLeft(6); val centerTop = cellTop(6); val centerSize = cellSize * 3
        val cx = centerLeft + centerSize / 2; val cy = centerTop + centerSize / 2
        val tl = Pair(centerLeft, centerTop); val tr = Pair(centerLeft + centerSize, centerTop)
        val bl = Pair(centerLeft, centerTop + centerSize); val br = Pair(centerLeft + centerSize, centerTop + centerSize)
        val center = Pair(cx, cy)
        fillPaint.color = colorGreen; drawTriangle(canvas, tl, tr, center)
        fillPaint.color = colorYellow; drawTriangle(canvas, tr, br, center)
        fillPaint.color = colorBlue; drawTriangle(canvas, br, bl, center)
        fillPaint.color = colorRed; drawTriangle(canvas, bl, tl, center)
    }

    private fun drawTriangle(canvas: Canvas, p1: Pair<Float, Float>, p2: Pair<Float, Float>, p3: Pair<Float, Float>) {
        val path = Path().apply {
            moveTo(p1.first, p1.second); lineTo(p2.first, p2.second); lineTo(p3.first, p3.second); close()
        }
        canvas.drawPath(path, fillPaint)
    }
}