package com.example.floatpad

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class PadShape { CIRCLE, ROUND_RECT }

data class PadButton(
    var label: String = "A",
    /** 中心 X，屏幕宽的比例 0~1 */
    var x: Float = 0.5f,
    /** 中心 Y，屏幕高的比例 0~1 */
    var y: Float = 0.5f,
    /** 直径，屏幕短边的比例 */
    var size: Float = 0.15f,
    var shape: PadShape = PadShape.CIRCLE,
    /** 底层贴图 id */
    var layer1: String = "base_ring",
    /** 上层贴图 id */
    var layer2: String = "none",
    /** 上层贴图相对按键半径的缩放 */
    var layer2Scale: Float = 0.72f,
    /** 是否上色：开启后两层贴图都套上 tintColor */
    var tinted: Boolean = true,
    var tintColor: Int = 0xFFFFFFFF.toInt(),
    var alpha: Float = 0.85f,
    /** 是否把「按下位置」和「触发位置」分开：为 true 时在 mapX/mapY 处注入点击 */
    var mapped: Boolean = false,
    /** 触发位置 X，屏幕宽的比例 */
    var mapX: Float = 0.5f,
    /** 触发位置 Y，屏幕高的比例 */
    var mapY: Float = 0.5f
)

object PadConfig {

    /** FNF 四键标准色 */
    const val LANE_LEFT = 0xFFC24B99.toInt()
    const val LANE_DOWN = 0xFF00FFFF.toInt()
    const val LANE_UP = 0xFF12FA05.toInt()
    const val LANE_RIGHT = 0xFFF9393F.toInt()

    private const val PREF = "pad"
    private const val KEY = "buttons_v3"

    // ================= 原版 assets/mobile 的按键布局 =================

    /**
     * 原版坐标空间：1280 x 720（FNF 标准虚拟分辨率），按键图形 124px。
     * 原版给的是图形左上角坐标（左键 x=0、P 键 y=2 都不会超出屏幕），这里换算成中心点比例。
     */
    private const val ORIG_W = 1280f
    private const val ORIG_H = 720f
    private const val ORIG_BTN = 124f

    /** 原版 json 里的一条按键：{ button, graphic, x, y, color } */
    data class RawBtn(val graphic: String, val x: Float, val y: Float, val color: String)

    data class OriginalMode(val name: String, val group: String, val buttons: List<RawBtn>)

    val ORIGINAL_MODES: List<OriginalMode> = listOf(
        // ---- assets/mobile/ActionModes ----
        OriginalMode(
            "A", "ActionModes（右侧动作键）",
            listOf(RawBtn("a", 1156f, 596f, "0xFF0000"))
        ),
        OriginalMode(
            "A_B", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("a", 1156f, 596f, "0xFF0000"),
                RawBtn("b", 1032f, 596f, "0xFFCB00")
            )
        ),
        OriginalMode(
            "A_B_C", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("a", 1156f, 596f, "0xFF0000"),
                RawBtn("b", 1032f, 596f, "0xFFCB00"),
                RawBtn("c", 908f, 596f, "0x44FF00")
            )
        ),
        OriginalMode(
            "A_B_C_D_V_X_Y_Z", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("v", 784f, 472f, "0x49A9B2"),
                RawBtn("d", 784f, 596f, "0x0078FF"),
                RawBtn("x", 908f, 472f, "0x99062D"),
                RawBtn("c", 908f, 596f, "0x44FF00"),
                RawBtn("y", 1032f, 472f, "0x4A35B9"),
                RawBtn("b", 1032f, 596f, "0xFFCB00"),
                RawBtn("z", 1156f, 472f, "0xCCB98E"),
                RawBtn("a", 1156f, 596f, "0xFF0000")
            )
        ),
        OriginalMode(
            "A_B_C_X_Y_Z", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("x", 908f, 472f, "0x99062D"),
                RawBtn("c", 908f, 596f, "0x44FF00"),
                RawBtn("y", 1032f, 472f, "0x4A35B9"),
                RawBtn("b", 1032f, 596f, "0xFFCB00"),
                RawBtn("z", 1156f, 472f, "0xCCB98E"),
                RawBtn("a", 1156f, 596f, "0xFF0000")
            )
        ),
        OriginalMode(
            "A_B_M_E", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("a", 1156f, 596f, "0xFF0000"),
                RawBtn("b", 1032f, 596f, "0xFFCB00"),
                RawBtn("m", 908f, 596f, "0x00BBFF"),
                RawBtn("e", 784f, 596f, "0xFF7D00")
            )
        ),
        OriginalMode(
            "A_B_X_Y", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("a", 1156f, 596f, "0xFF0000"),
                RawBtn("b", 1032f, 596f, "0xFFCB00"),
                RawBtn("x", 908f, 596f, "0x99062D"),
                RawBtn("y", 784f, 596f, "0x4A35B9")
            )
        ),
        OriginalMode(
            "B", "ActionModes（右侧动作键）",
            listOf(RawBtn("b", 1156f, 596f, "0xFFCB00"))
        ),
        OriginalMode(
            "B_C", "ActionModes（右侧动作键）",
            listOf(
                RawBtn("c", 1032f, 596f, "0x44FF00"),
                RawBtn("b", 1156f, 596f, "0xFFCB00")
            )
        ),
        OriginalMode(
            "P", "ActionModes（右侧动作键）",
            listOf(RawBtn("p", 1156f, 2f, "0xE5DE00"))
        ),
        OriginalMode(
            "Z", "ActionModes（右侧动作键）",
            listOf(RawBtn("z", 1056f, 596f, "0xFF0000"))
        ),

        // ---- assets/mobile/DPadModes ----
        OriginalMode(
            "LEFT_FULL", "DPadModes（左侧方向键）",
            listOf(
                RawBtn("up", 98f, 405f, "0xFF12FA05"),
                RawBtn("left", 0f, 500f, "0xFFC24B99"),
                RawBtn("right", 196f, 500f, "0xFFF9393F"),
                RawBtn("down", 98f, 596f, "0xFF00FFFF")
            )
        ),
        OriginalMode(
            "LEFT_RIGHT", "DPadModes（左侧方向键）",
            listOf(
                RawBtn("left", 0f, 587f, "0xFFC24B99"),
                RawBtn("right", 127f, 587f, "0xFFF9393F")
            )
        ),
        OriginalMode(
            "RIGHT_FULL", "DPadModes（左侧方向键）",
            listOf(
                RawBtn("up", 1022f, 314f, "0xFF12FA05"),
                RawBtn("left", 896f, 413f, "0xFFC24B99"),
                RawBtn("right", 1148f, 413f, "0xFFF9393F"),
                RawBtn("down", 1022f, 521f, "0xFF00FFFF")
            )
        ),
        OriginalMode(
            "UP_DOWN", "DPadModes（左侧方向键）",
            listOf(
                RawBtn("up", 0f, 472f, "0xFF12FA05"),
                RawBtn("down", 0f, 596f, "0xFF00FFFF")
            )
        )
    )

    /** 把原版模式转成一套新按键（每次调用都返回新对象，可以直接改） */
    fun buildOriginal(mode: OriginalMode): MutableList<PadButton> =
        mode.buttons.map { fromOriginal(it) }.toMutableList()

    private fun fromOriginal(raw: RawBtn): PadButton {
        val half = ORIG_BTN / 2f
        return PadButton(
            label = raw.graphic.uppercase(),
            x = ((raw.x + half) / ORIG_W).coerceIn(0.02f, 0.98f),
            y = ((raw.y + half) / ORIG_H).coerceIn(0.02f, 0.98f),
            size = ORIG_BTN / ORIG_H,
            shape = PadShape.CIRCLE,
            layer1 = "base_ring",
            // 有原版图就直接用原版图，没有才退回内置字母/箭头
            layer2 = Textures.assetIdForGraphic(raw.graphic) ?: graphicToLayer2(raw.graphic),
            layer2Scale = 0.72f,
            tinted = true,
            tintColor = parseHexColor(raw.color)
        )
    }

    /** 方向键用内置箭头，其余字母用 letter: 动态画字 */
    private fun graphicToLayer2(graphic: String): String = when (graphic.lowercase()) {
        "up" -> "icon_arrow_up"
        "down" -> "icon_arrow_down"
        "left" -> "icon_arrow_left"
        "right" -> "icon_arrow_right"
        else -> "letter:${graphic.lowercase()}"
    }

    /** 原版颜色有 8 位（ARGB）也有 6 位（RGB），统一成 ARGB */
    private fun parseHexColor(hex: String): Int {
        val h = hex.removePrefix("0x").removePrefix("0X")
        val v = runCatching { h.toLong(16) }.getOrDefault(0xFFFFFFFFL)
        return if (h.length >= 8) v.toInt() else (0xFF000000L or v).toInt()
    }

    // ================= 存取 =================

    fun load(ctx: Context): MutableList<PadButton> {
        val raw = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString(KEY, null)
        if (raw.isNullOrBlank()) return presetFnf4()
        return runCatching { fromJson(raw) }.getOrElse { presetFnf4() }
    }

    fun save(ctx: Context, list: List<PadButton>) {
        ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit().putString(KEY, toJson(list)).apply()
    }

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
                put("mapped", b.mapped)
                put("mapX", b.mapX.toDouble())
                put("mapY", b.mapY.toDouble())
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
                    size = o.optDouble("size", 0.15).toFloat(),
                    shape = runCatching { PadShape.valueOf(o.optString("shape")) }
                        .getOrDefault(PadShape.CIRCLE),
                    layer1 = o.optString("layer1", "base_ring"),
                    layer2 = o.optString("layer2", "none"),
                    layer2Scale = o.optDouble("layer2Scale", 0.72).toFloat(),
                    tinted = o.optBoolean("tinted", true),
                    tintColor = o.optInt("tintColor", 0xFFFFFFFF.toInt()),
                    alpha = o.optDouble("alpha", 0.85).toFloat(),
                    mapped = o.optBoolean("mapped", false),
                    mapX = o.optDouble("mapX", 0.5).toFloat(),
                    mapY = o.optDouble("mapY", 0.5).toFloat()
                )
            )
        }
        return out
    }

    // ================= 自带预设 =================

    fun newButton(index: Int) = PadButton(
        label = "B${index + 1}",
        layer1 = "base_soft",
        layer2 = "none",
        tintColor = 0xFFFFFFFF.toInt()
    )

    /** FNF 四键 */
    fun presetFnf4() = mutableListOf(
        PadButton("←", 0.10f, 0.78f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_arrow_left", 0.72f, true, LANE_LEFT),
        PadButton("↓", 0.27f, 0.78f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_arrow_down", 0.72f, true, LANE_DOWN),
        PadButton("↑", 0.10f, 0.60f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_arrow_up", 0.72f, true, LANE_UP),
        PadButton("→", 0.27f, 0.60f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_arrow_right", 0.72f, true, LANE_RIGHT)
    )

    /** FNF 六键（四键 + A/B） */
    fun presetFnf6() = presetFnf4().apply {
        add(PadButton("A", 0.73f, 0.78f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_a", 0.70f, true, 0xFFFFD400.toInt()))
        add(PadButton("B", 0.90f, 0.78f, 0.15f, PadShape.CIRCLE, "base_ring", "icon_b", 0.70f, true, 0xFF3BD6FF.toInt()))
    }

    /** 十字键：换成圆角方样式 */
    fun presetDpad() = mutableListOf(
        PadButton("←", 0.12f, 0.80f, 0.16f, PadShape.ROUND_RECT, "base_square", "icon_arrow_left", 0.70f, true, LANE_LEFT),
        PadButton("↓", 0.28f, 0.80f, 0.16f, PadShape.ROUND_RECT, "base_square", "icon_arrow_down", 0.70f, true, LANE_DOWN),
        PadButton("↑", 0.12f, 0.62f, 0.16f, PadShape.ROUND_RECT, "base_square", "icon_arrow_up", 0.70f, true, LANE_UP),
        PadButton("→", 0.28f, 0.62f, 0.16f, PadShape.ROUND_RECT, "base_square", "icon_arrow_right", 0.70f, true, LANE_RIGHT)
    )
}
