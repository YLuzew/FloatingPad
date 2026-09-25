package com.example.floatpad

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import kotlin.math.hypot

@SuppressLint("ViewConstructor")
class PadButtonView(
    context: Context,
    val cfg: PadButton,
    /** 真正要注入点击的屏幕坐标（已把按键映射算进去） */
    private val resolveTap: () -> Pair<Float, Float>,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit,
    /** 编辑模式下长按：给这个按键取映射点 */
    private val onRequestMap: () -> Unit
) : View(context) {

    lateinit var lp: WindowManager.LayoutParams
    var index: Int = 0

    var editMode = false
        set(value) {
            field = value
            invalidate()
        }

    private var dragging = false
    private var lastRawX = 0f
    private var lastRawY = 0f

    private val mapRunnable = Runnable { onRequestMap() }

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        color = 0xFFFFC107.toInt()
    }
    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF3BD6FF.toInt()
    }

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f

        val filter =
            if (cfg.tinted) PorterDuffColorFilter(cfg.tintColor, PorterDuff.Mode.SRC_IN) else null

        // 1. 底盘形状
        val shapePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            alpha = (cfg.alpha * 90).toInt()
        }
        when (cfg.shape) {
            PadShape.CIRCLE -> canvas.drawCircle(cx, cy, r, shapePaint)
            PadShape.ROUND_RECT -> canvas.drawRoundRect(
                RectF(0f, 0f, width.toFloat(), height.toFloat()), r * 0.5f, r * 0.5f, shapePaint
            )
        }

        // 2. layer1：底盘贴图
        basePaint.color = Color.WHITE
        basePaint.alpha = (cfg.alpha * 255).toInt()
        basePaint.colorFilter = filter
        Textures.draw(canvas, cfg.layer1, cx, cy, r * 0.92f, basePaint)

        // 3. layer2：图标层
        if (cfg.layer2 == Textures.NONE) {
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (cfg.tinted) cfg.tintColor else Color.WHITE
                textAlign = Paint.Align.CENTER
                textSize = r * 0.95f
                isFakeBoldText = true
                alpha = (cfg.alpha * 255).toInt()
            }
            canvas.drawText(cfg.label, cx, cy + textPaint.textSize / 3f, textPaint)
        } else {
            iconPaint.color = Color.WHITE
            iconPaint.alpha = 255
            iconPaint.colorFilter = filter
            Textures.draw(canvas, cfg.layer2, cx, cy, r * cfg.layer2Scale, iconPaint)
        }

        if (editMode) {
            // 虚线框 + 序号
            canvas.drawCircle(cx, cy, r - 2f, editPaint)
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFC107.toInt()
                textSize = r * 0.4f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${index + 1}", cx, cy - r * 0.62f, numPaint)

            // 已设映射的按键右上角打个十字靶标
            if (cfg.mapped) {
                val br = r * 0.24f
                val bx = cx + r * 0.66f
                val by = cy - r * 0.66f
                canvas.drawCircle(bx, by, br, badgePaint)
                canvas.drawLine(bx - br, by, bx + br, by, badgePaint)
                canvas.drawLine(bx, by - br, bx, by + br, badgePaint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                if (editMode) {
                    // 编辑模式：长按 0.6 秒 = 给这个按键取映射点
                    postDelayed(mapRunnable, 600L)
                } else {
                    // 游戏模式：按下即注入，尽量把延迟做小
                    val p = resolveTap()
                    PadAccessibilityService.instance?.tap(p.first, p.second)
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (editMode) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    if (hypot(dx, dy) >= 4f) {
                        removeCallbacks(mapRunnable)
                        dragging = true
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        onDrag(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(mapRunnable)
                if (dragging) onDragEnd()
                dragging = false
                return true
            }
        }
        return super.onTouchEvent(event)
    }
}

/** 左上角小齿轮：进出编辑模式 */
@SuppressLint("ViewConstructor")
class ToggleView(
    context: Context,
    private val onToggle: () -> Unit
) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f
        paint.color = 0xB3000000.toInt()
        canvas.drawCircle(cx, cy, r, paint)
        paint.color = Color.WHITE
        paint.textAlign = Paint.Align.CENTER
        paint.textSize = r * 1.1f
        canvas.drawText("⚙", cx, cy + paint.textSize / 3f, paint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            performClick()
            onToggle()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()
}
