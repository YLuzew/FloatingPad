package com.example.floatpad

import android.content.Context
import android.view.KeyEvent
import org.json.JSONArray
import org.json.JSONObject

enum class PadShape { CIRCLE, ROUND_RECT }

/** 三种可切换的布局 */
enum class PadMode(val title: String) {
    DPAD("十字键"),
    DPAD_ACTIONS("十字键 + 控制键"),
    HITBOX("Hitbox")
}

data class PadButton(
    var label: String = "A",
    /** 相对「居中 16:9 区域」的比例，0~1 */
    var x: Float = 0.5f,
    var y: Float = 0.5f,
    /** 直径，相对 16:9 区域高度的比例 */
    var size: Float = 0.13f,
    var shape: PadShape = PadShape.CIRCLE,
    var layer1: String = "base_ring",
    var layer2: String = "none",
    var layer2Scale: Float = 0.72f,
    var tinted: Boolean = true,
    var tintColor: Int = 0xFFFFFFFF.toInt(),
    var alpha: Float = 0.85f,
    /** 绑定的键盘键码；0 = 不绑定，退回触摸点击 */
    var keyCode: Int = 0
)

data class KeyOption(val name: String, val code: Int)

object PadConfig {

    /** FNF 四键标准色 */
    const val LANE_LEFT = 0xFFC24B99.toInt()
    const val LANE_DOWN = 0xFF00FFFF.toInt()
    const val LANE_UP = 0xFF12FA05.toInt()
    const val LANE_RIGHT = 0xFFF9393F.toInt()

    private const val PREF = "pad"
    private const val KEY = "buttons_v4"
    private const val KEY_MODE = "mode"

    // ================= 可绑定的按键 =================

    private val LETTERS = ('A'..'Z').map {
        KeyOption(it.toString(), KeyEvent.KEYCODE_A + (it - 'A'))
    }
    private val DIGITS = ('0'..'9').map {
        KeyOption(it.toString(), KeyEvent.KEYCODE_0 + (it - '0'))
    }
    private val FUNCTION = (1..12).map {
        KeyOption("F$it", KeyEvent.KEYCODE_F1 + (it - 1))
    }

    val KEYS: List<KeyOption> = listOf(
        KeyOption("不绑定", 0),
        KeyOption("←", KeyEvent.KEYCODE_DPAD_LEFT),
        KeyOption("→", KeyEvent.KEYCODE_DPAD_RIGHT),
        KeyOption("↑", KeyEvent.KEYCODE_DPAD_UP),
        KeyOption("↓", KeyEvent.KEYCODE_DPAD_DOWN),
        KeyOption("Enter", KeyEvent.KEYCODE_ENTER),
        KeyOption("Esc", KeyEvent.KEYCODE_ESCAPE),
        KeyOption("Space", KeyEvent.KEYCODE_SPACE),
        KeyOption("Tab", KeyEvent.KEYCODE_TAB),
        KeyOption("Shift", KeyEvent.KEYCODE_SHIFT_LEFT),
        KeyOption("Ctrl", KeyEvent.KEYCODE_CTRL_LEFT),
        KeyOption("Alt", KeyEvent.KEYCODE_ALT_LEFT),
        KeyOption("Del", KeyEvent.KEYCODE_DEL),
        KeyOption("Back", KeyEvent.KEYCODE_BACK)
    ) + LETTERS + DIGITS + FUNCTION

    fun keyName(code: Int): String =
        KEYS.firstOrNull { it.code == code }?.name
            ?: if (code == 0) "未绑定" else "Key$code"

    // ================= 居中 16:9 区域 =================

    /** 返回 [left, top, width, height]（像素）：屏幕里居中的那块 16:9 */
    fun viewport(screenW: Int, screenH: Int): FloatArray {
        val target = 16f / 9f
        var w = screenW.toFloat()
        var h = screenH.toFloat()
        if (w / h > target) w = h * target else h = w / target
        return floatArrayOf((screenW - w) / 2f, (screenH - h) / 2f, w, h)
    }

    // ================= 三种布局 =================

    fun modeButtons(mode: PadMode): MutableList<PadButton> = when (mode) {
        PadMode.DPAD -> mutableListOf(
            btn("↑", 0.16f, 0.62f, "icon_arrow_up", KeyEvent.KEYCODE_DPAD_UP, LANE_UP),
            btn("←", 0.06f, 0.74f, "icon_arrow_left", KeyEvent.KEYCODE_DPAD_LEFT, LANE_LEFT),
            btn("→", 0.26f, 0.74f, "icon_arrow_right", KeyEvent.KEYCODE_DPAD_RIGHT, LANE_RIGHT),
            btn("↓", 0.16f, 0.86f, "icon_arrow_down", KeyEvent.KEYCODE_DPAD_DOWN, LANE_DOWN)
        )

        PadMode.DPAD_ACTIONS ->
            modeButtons(PadMode.DPAD).apply {
                add(btn("A", 0.78f, 0.80f, "letter:a", KeyEvent.KEYCODE_ENTER, 0xFFFFD400.toInt()))
                add(btn("B", 0.90f, 0.80f, "letter:b", KeyEvent.KEYCODE_ESCAPE, 0xFF3BD6FF.toInt()))
            }

        PadMode.HITBOX -> mutableListOf(
            btn("←", 0.08f, 0.82f, "icon_arrow_left", KeyEvent.KEYCODE_DPAD_LEFT, LANE_LEFT),
            btn("↓", 0.20f, 0.82f, "icon_arrow_down", KeyEvent.KEYCODE_DPAD_DOWN, LANE_DOWN),
            btn("↑", 0.32f, 0.82f, "icon_arrow_up", KeyEvent.KEYCODE_DPAD_UP, LANE_UP),
            btn("→", 0.44f, 0.82f, "icon_arrow_right", KeyEvent.KEYCODE_DPAD_RIGHT, LANE_RIGHT)
        )
    }

    private fun btn(
        label: String,
        x: Float,
        y: Float,
        icon: String,
        key: Int,
        color: Int
    ) = PadButton(
        label = label,
        x = x,
        y = y,
        size = 0.13f,
        shape = PadShape.CIRCLE,
        layer1 = "base_ring",
        layer2 = icon,
        layer2Scale = 0.72f,
        tinted = true,
        tintColor = color,
        alpha = 0.85f,
        keyCode = key
    )

    fun newButton(index: Int) = PadButton(
        label = "K${index + 1}",
        layer1 = "base_soft",
        layer2 = "none",
        tintColor = 0xFFFFFFFF.toInt()
    )

    // ================= 存取 =================

    fun loadMode(ctx: Context): PadMode {
        val name = prefs(ctx).getString(KEY_MODE, PadMode.DPAD_ACTIONS.name)
        return runCatching { PadMode.valueOf(name!!) }.getOrDefault(PadMode.DPAD_ACTIONS)
    }

    fun saveMode(ctx: Context, mode: PadMode) {
        prefs(ctx).edit().putString(KEY_MODE, mode.name).apply()
    }

    fun load(ctx: Context): MutableList<PadButton> {
        val raw = prefs(ctx).getString(KEY, null)
        if (raw.isNullOrBlank()) return modeButtons(loadMode(ctx))
        return runCatching { fromJson(raw) }.getOrElse { modeButtons(loadMode(ctx)) }
    }

    fun save(ctx: Context, list: List<PadButton>) {
        prefs(ctx).edit().putString(KEY, toJson(list)).apply()
    }

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun toJson(list: List<PadButton>): String {
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(JSONObject().apply {
                put("label", b.label)
                put("x", b.x.toDouble())
                put("y", b.y.toDouble())
                put("size", b.size.toDouble())
                put("shape", b.shape.name)
                put("layer1", b.layer1)
                put("layer2", b.layer2)
                put("layer2Scale", b.layer2Scale.toDouble())
                put("tinted", b.tinted)
                put("tintColor", b.tintColor)
                put("alpha", b.alpha.toDouble())
                put("keyCode", b.keyCode)
            })
        }
        return arr.toString()
    }

    private fun fromJson(raw: String): MutableList<PadButton> {
        val arr = JSONArray(raw)
        val out = mutableListOf<PadButton>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(
                PadButton(
                    label = o.optString("label", "A"),
                    x = o.optDouble("x", 0.5).toFloat(),
                    y = o.optDouble("y", 0.5).toFloat(),
                    size = o.optDouble("size", 0.13).toFloat(),
                    shape = runCatching { PadShape.valueOf(o.optString("shape")) }
                        .getOrDefault(PadShape.CIRCLE),
                    layer1 = o.optString("layer1", "base_ring"),
                    layer2 = o.optString("layer2", "none"),
                    layer2Scale = o.optDouble("layer2Scale", 0.72).toFloat(),
                    tinted = o.optBoolean("tinted", true),
                    tintColor = o.optInt("tintColor", 0xFFFFFFFF.toInt()),
                    alpha = o.optDouble("alpha", 0.85).toFloat(),
                    keyCode = o.optInt("keyCode", 0)
                )
            )
        }
        return out
    }
}
