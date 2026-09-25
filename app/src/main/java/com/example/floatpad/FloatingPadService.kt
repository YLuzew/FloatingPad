package com.example.floatpad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingPadService : Service() {

    private lateinit var wm: WindowManager
    private val buttons = mutableListOf<PadButton>()
    private val buttonViews = mutableListOf<PadButtonView>()
    private var toggleView: ToggleView? = null
    private var captureView: View? = null

    private var screenW = 0
    private var screenH = 0
    private var editMode = false

    override fun onCreate() {
        super.onCreate()
        running = true
        startForeground(NOTIFY_ID, buildNotification())
        Textures.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        readScreenSize()
        buttons.addAll(PadConfig.load(this))
        buttons.forEachIndexed { i, cfg -> addButtonWindow(cfg, i) }
        addToggleWindow()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_RELOAD) rebuildButtons()
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        endMapCapture()
        buttonViews.forEach { runCatching { wm.removeView(it) } }
        buttonViews.clear()
        toggleView?.let { runCatching { wm.removeView(it) } }
        toggleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 构建 / 重建 ----------------

    /** 配置变了：拆掉旧按键重新按新配置建，数量、贴图、样式全跟着变 */
    private fun rebuildButtons() {
        buttonViews.forEach { runCatching { wm.removeView(it) } }
        buttonViews.clear()
        buttons.clear()
        buttons.addAll(PadConfig.load(this))
        buttons.forEachIndexed { i, cfg -> addButtonWindow(cfg, i) }
    }

    private fun addButtonWindow(cfg: PadButton, index: Int) {
        val size = sizePx(cfg.size)
        val view = PadButtonView(
            this,
            cfg,
            resolveTap = { tapPointOf(cfg) },
            onDrag = { dx, dy -> moveButton(cfg, dx, dy) },
            onDragEnd = { PadConfig.save(this, buttons) },
            onRequestMap = { startMapCapture(cfg) }
        )
        view.index = index
        view.editMode = editMode
        val lp = buildLayoutParams(size, cfg)
        view.lp = lp
        runCatching { wm.addView(view, lp) }
        buttonViews.add(view)
    }

    /** 映射过了就打在映射点上，否则就打在按键自己的位置上 */
    private fun tapPointOf(cfg: PadButton): Pair<Float, Float> =
        if (cfg.mapped) {
            cfg.mapX * screenW to cfg.mapY * screenH
        } else {
            cfg.x * screenW to cfg.y * screenH
        }

    private fun moveButton(cfg: PadButton, dx: Float, dy: Float) {
        cfg.x = (cfg.x + dx / screenW).coerceIn(0.02f, 0.98f)
        cfg.y = (cfg.y + dy / screenH).coerceIn(0.02f, 0.98f)
        buttonViews.firstOrNull { it.cfg === cfg }?.let { applyLayout(it, cfg) }
    }

    private fun applyLayout(v: PadButtonView, cfg: PadButton) {
        val size = sizePx(cfg.size)
        v.lp.width = size
        v.lp.height = size
        v.lp.x = (cfg.x * screenW - size / 2f).toInt()
        v.lp.y = (cfg.y * screenH - size / 2f).toInt()
        runCatching { wm.updateViewLayout(v, v.lp) }
    }

    private fun buildLayoutParams(size: Int, cfg: PadButton): WindowManager.LayoutParams {
        return WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (cfg.x * screenW - size / 2f).toInt()
            y = (cfg.y * screenH - size / 2f).toInt()
        }
    }

    // ---------------- 取映射点 ----------------

    /**
     * 铺一层全屏透明层，用户点哪里，这个按键的映射点就是哪里。
     * 取点过程中点击不会传到游戏，所以不会误触。
     */
    private fun startMapCapture(cfg: PadButton) {
        if (captureView != null) return
        val view = object : View(this) {
            override fun onDraw(canvas: Canvas) {
                super.onDraw(canvas)
                val hint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0xCCFFFFFF.toInt()
                    textAlign = Paint.Align.CENTER
                    textSize = 48f
                }
                canvas.drawText("点一下游戏里这个按键的位置", width / 2f, height / 2f, hint)
                val frame = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = 0x88FFC107.toInt()
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                }
                canvas.drawRect(6f, 6f, width - 6f, height - 6f, frame)
            }

            override fun onTouchEvent(event: MotionEvent): Boolean {
                if (event.actionMasked == MotionEvent.ACTION_UP) {
                    cfg.mapX = (event.rawX / screenW).coerceIn(0f, 1f)
                    cfg.mapY = (event.rawY / screenH).coerceIn(0f, 1f)
                    cfg.mapped = true
                    PadConfig.save(this@FloatingPadService, buttons)
                    endMapCapture()
                    buttonViews.firstOrNull { it.cfg === cfg }?.invalidate()
                    Toast.makeText(
                        this@FloatingPadService,
                        "映射已设置（点齿轮可继续调）",
                        Toast.LENGTH_SHORT
                    ).show()
                }
                return true
            }
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        )
        runCatching { wm.addView(view, lp) }
        captureView = view
    }

    private fun endMapCapture() {
        captureView?.let { runCatching { wm.removeView(it) } }
        captureView = null
    }

    // ---------------- 齿轮窗口 ----------------

    private fun addToggleWindow() {
        val density = resources.displayMetrics.density
        val size = (TOGGLE_SIZE * density).toInt()
        val margin = (TOGGLE_MARGIN * density).toInt()
        val view = ToggleView(this) { setEditMode(!editMode) }
        val lp = WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = margin
            y = margin
        }
        runCatching { wm.addView(view, lp) }
        toggleView = view
    }

    private fun setEditMode(on: Boolean) {
        editMode = on
        buttonViews.forEach { it.editMode = on }
    }

    // ---------------- 杂项 ----------------

    private fun sizePx(ratio: Float) = (ratio * minOf(screenW, screenH)).toInt()

    private fun readScreenSize() {
        val m = resources.displayMetrics
        screenW = m.widthPixels
        screenH = m.heightPixels
    }

    /** 横竖屏切换时重排，避免位置跑飞 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readScreenSize()
        buttonViews.forEach { applyLayout(it, it.cfg) }
    }

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "悬浮按键", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentTitle("悬浮按键运行中")
            .setContentText("点齿轮编辑；长按按键可取映射点")
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val NOTIFY_ID = 1001
        private const val CHANNEL_ID = "floatpad"
        private const val ACTION_RELOAD = "com.example.floatpad.RELOAD"

        /** 齿轮直径与边距，单位 dp */
        private const val TOGGLE_SIZE = 44f
        private const val TOGGLE_MARGIN = 10f

        @Volatile
        var running = false
            private set

        /** 服务在跑才刷新，避免“改个配置就把悬浮层拉起来” */
        fun reload(ctx: Context) {
            if (!running) return
            val intent = Intent(ctx, FloatingPadService::class.java).setAction(ACTION_RELOAD)
            runCatching { ctx.startService(intent) }
        }
    }
}
