package com.example.floatpad

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.Gravity
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat

class FloatingPadService : Service() {

    private lateinit var wm: WindowManager
    private val buttons = mutableListOf<PadButton>()
    private val buttonViews = mutableListOf<PadButtonView>()
    private var toggleView: ToggleView? = null

    private var screenW = 0
    private var screenH = 0

    /** 屏幕里居中的那块 16:9 */
    private var viewLeft = 0f
    private var viewTop = 0f
    private var viewW = 0f
    private var viewH = 0f

    private var editMode = false

    override fun onCreate() {
        super.onCreate()
        running = true
        startForeground(NOTIFY_ID, buildNotification())
        Textures.init(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        readMetrics()
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
        buttonViews.forEach { runCatching { wm.removeView(it) } }
        buttonViews.clear()
        toggleView?.let { runCatching { wm.removeView(it) } }
        toggleView = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ---------------- 构建 / 重建 ----------------

    /** 切布局或改配置后：拆掉旧按键按新配置重建 */
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
            onPress = { press(cfg) },
            onDrag = { dx, dy -> moveButton(cfg, dx, dy) },
            onDragEnd = { PadConfig.save(this, buttons) }
        )
        view.index = index
        view.editMode = editMode
        val lp = buildLayoutParams(size, cfg)
        view.lp = lp
        runCatching { wm.addView(view, lp) }
        buttonViews.add(view)
    }

    /**
     * 按下时干什么：
     * 绑了键 → 交给输入法发键盘事件；没绑键 → 退回触摸点击。
     */
    private fun press(cfg: PadButton) {
        if (cfg.keyCode != 0) {
            val sent = PadInputMethodService.instance?.sendKey(cfg.keyCode) ?: false
            if (!sent) {
                Toast.makeText(
                    this,
                    "发不出按键：请先把输入法切成「悬浮按键」",
                    Toast.LENGTH_SHORT
                ).show()
            }
        } else {
            val (cx, cy) = centerOf(cfg)
            PadAccessibilityService.instance?.tap(cx, cy)
        }
    }

    private fun centerOf(cfg: PadButton): Pair<Float, Float> =
        (viewLeft + cfg.x * viewW) to (viewTop + cfg.y * viewH)

    private fun moveButton(cfg: PadButton, dx: Float, dy: Float) {
        cfg.x = (cfg.x + dx / viewW).coerceIn(0.01f, 0.99f)
        cfg.y = (cfg.y + dy / viewH).coerceIn(0.01f, 0.99f)
        buttonViews.firstOrNull { it.cfg === cfg }?.let { applyLayout(it, cfg) }
    }

    private fun applyLayout(v: PadButtonView, cfg: PadButton) {
        val size = sizePx(cfg.size)
        val (cx, cy) = centerOf(cfg)
        v.lp.width = size
        v.lp.height = size
        v.lp.x = (cx - size / 2f).toInt()
        v.lp.y = (cy - size / 2f).toInt()
        runCatching { wm.updateViewLayout(v, v.lp) }
    }

    private fun buildLayoutParams(size: Int, cfg: PadButton): WindowManager.LayoutParams {
        val (cx, cy) = centerOf(cfg)
        return WindowManager.LayoutParams(
            size, size,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (cx - size / 2f).toInt()
            y = (cy - size / 2f).toInt()
        }
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

    /** 按键直径 = 比例 × 16:9 区域的高度 */
    private fun sizePx(ratio: Float) = (ratio * viewH).toInt()

    private fun readMetrics() {
        val m = resources.displayMetrics
        screenW = m.widthPixels
        screenH = m.heightPixels
        val v = PadConfig.viewport(screenW, screenH)
        viewLeft = v[0]
        viewTop = v[1]
        viewW = v[2]
        viewH = v[3]
    }

    /** 横竖屏切换时重算 16:9 区域并重排 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        readMetrics()
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
            .setContentText("点齿轮可拖动按键；回控制页可切换布局")
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
