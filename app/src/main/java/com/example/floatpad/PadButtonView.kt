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
    /** 按下时交给服务决定：发按键还是发触摸 */
    private val onPress: () -> Unit,
    private val onDrag: (Float, Float) -> Unit,
    private val onDragEnd: () -> Unit
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

    private val basePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val editPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(14f, 12f), 0f)
        color = 0xFFFFC107.toInt()
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

        // 4. 绑了键就在底下标出来，一眼能看出这个键代表什么
        if (!editMode) {
            val name = if (cfg.keyCode == 0) "触摸" else PadConfig.keyName(cfg.keyCode)
            val tagPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = if (cfg.keyCode == 0) 0x99FFAB40.toInt() else 0xCCFFFFFF.toInt()
                textAlign = Paint.Align.CENTER
                textSize = r * 0.42f
                isFakeBoldText = true
            }
            canvas.drawText(name, cx, cy + r * 0.82f, tagPaint)
        }

        // 5. 编辑模式：虚线框 + 序号
        if (editMode) {
            canvas.drawCircle(cx, cy, r - 2f, editPaint)
            val numPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = 0xFFFFC107.toInt()
                textSize = r * 0.4f
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText("${index + 1}", cx, cy - r * 0.62f, numPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastRawX = event.rawX
                lastRawY = event.rawY
                dragging = false
                if (!editMode) {
                    // 按下即触发，尽量把延迟做小
                    onPress()
                }
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (editMode) {
                    val dx = event.rawX - lastRawX
                    val dy = event.rawY - lastRawY
                    if (hypot(dx, dy) >= 4f) {
                        dragging = true
                        lastRawX = event.rawX
                        lastRawY = event.rawY
                        onDrag(dx, dy)
                    }
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
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
