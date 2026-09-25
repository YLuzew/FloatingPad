package com.example.floatpad

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat

class PadEditorActivity : AppCompatActivity() {

    private val buttons = mutableListOf<PadButton>()
    private var index = 0
    private lateinit var root: LinearLayout

    private val pickLayer1 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { Textures.importFrom(this, it) }?.let {
            cur().layer1 = it
            render()
        }
    }

    private val pickLayer2 = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { Textures.importFrom(this, it) }?.let {
            cur().layer2 = it
            render()
        }
    }

    private val pickFolder =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            uri?.let {
                val n = Textures.importFolder(this, it)
                toast("导入了 $n 张贴图")
                render()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Textures.init(this)
        buttons.addAll(PadConfig.load(this))
        if (buttons.isEmpty()) buttons.add(PadConfig.newButton(0))

        val scroll = ScrollView(this)
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 90, 40, 90)
        }
        scroll.addView(root)
        setContentView(scroll)
        render()
    }

    private fun cur(): PadButton = buttons[index.coerceIn(0, buttons.size - 1)]

    // ---------------- 界面 ----------------

    private fun render() {
        root.removeAllViews()
        index = index.coerceIn(0, buttons.size - 1)
        val b = cur()

        val extraIds = Textures.resIds() + Textures.assetIds() + Textures.importedIds()
        val extraNames = Textures.resNames() + Textures.assetLabels() + Textures.importedNames()
        val baseIds = Textures.BASE_IDS + extraIds
        val baseNames = Textures.BASE_NAMES + extraNames
        val iconIds = Textures.ICON_IDS + extraIds
        val iconNames = Textures.ICON_NAMES + extraNames

        // 布局切换
        root.addView(title("布局（点一下就整套换）"))
        val modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val currentMode = PadConfig.loadMode(this)
        PadMode.entries.forEach { mode ->
            val mb = Button(this).apply {
                text = if (mode == currentMode) "● ${mode.title}" else mode.title
                textSize = 11f
                setOnClickListener {
                    PadConfig.saveMode(this@PadEditorActivity, mode)
                    buttons.clear()
                    buttons.addAll(PadConfig.modeButtons(mode))
                    index = 0
                    PadConfig.save(this@PadEditorActivity, buttons)
                    FloatingPadService.reload(this@PadEditorActivity)
                    render()
                }
            }
            modeRow.addView(
                mb,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(modeRow)

        // 当前按键
        root.addView(title("第 ${index + 1} / ${buttons.size} 个按键"))
        root.addView(
            row(
                chip("← 上一个") {
                    index = (index - 1 + buttons.size) % buttons.size
                    render()
                },
                chip("下一个 →") {
                    index = (index + 1) % buttons.size
                    render()
                },
                chip("＋ 新增") {
                    buttons.add(PadConfig.newButton(buttons.size))
                    index = buttons.size - 1
                    render()
                },
                chip("删除") {
                    if (buttons.size > 1) {
                        buttons.removeAt(index)
                        index = index.coerceAtMost(buttons.size - 1)
                        render()
                    } else {
                        toast("至少留一个按键")
                    }
                }
            )
        )

        // 键位绑定
        root.addView(title("绑定的键"))
        root.addView(
            chip(if (b.keyCode == 0) "未绑定（触摸点击）" else "当前：${PadConfig.keyName(b.keyCode)}") {
                pickKey(b)
            }
        )
        root.addView(title("绑了键就用输入法发键盘事件；不绑就退回触摸点击。"))

        root.addView(title("Hitbox 样式"))
        root.addView(
            row(
                chip("圆形") {
                    b.shape = PadShape.CIRCLE
                    render()
                },
                chip("圆角方") {
                    b.shape = PadShape.ROUND_RECT
                    render()
                }
            )
        )

        root.addView(title("底层贴图（底盘）：${Textures.displayName(b.layer1)}"))
        root.addView(grid(baseIds, baseNames, b.layer1) {
            b.layer1 = it
            render()
        })
        root.addView(chip("从相册选底层贴图") { pickLayer1.launch("image/*") })

        root.addView(title("上层贴图（图标层）：${Textures.displayName(b.layer2)}"))
        root.addView(grid(iconIds, iconNames, b.layer2) {
            b.layer2 = it
            render()
        })
        root.addView(chip("从相册选上层贴图") { pickLayer2.launch("image/*") })
        root.addView(chip("从文件夹批量导入贴图") { pickFolder.launch(null) })

        root.addView(slider("上层缩放", b.layer2Scale, 0.30f, 1.00f) { b.layer2Scale = it })
        root.addView(slider("按键大小", b.size, 0.06f, 0.30f) { b.size = it })
        root.addView(slider("不透明度", b.alpha, 0.20f, 1.00f) { b.alpha = it })

        root.addView(
            SwitchCompat(this).apply {
                text = "是否上色（给两层贴图套色）"
                isChecked = b.tinted
                setOnCheckedChangeListener { _, value ->
                    b.tinted = value
                    render()
                }
            }
        )

        root.addView(title("上色颜色"))
        val colorRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        COLOR_NAMES.forEachIndexed { i, name ->
            val c = COLOR_VALUES[i]
            val colorChip = Button(this).apply {
                text = name
                textSize = 12f
                setTextColor(if (isLight(c)) Color.BLACK else Color.WHITE)
                background = GradientDrawable().apply {
                    setColor(c)
                    cornerRadius = 12f
                }
                setOnClickListener {
                    b.tintColor = c
                    b.tinted = true
                    render()
                }
            }
            colorRow.addView(
                colorChip,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        root.addView(colorRow)

        root.addView(title("悬浮层在跑的话，保存后会立即刷新"))
        root.addView(
            chip("保存并应用") {
                PadConfig.save(this, buttons)
                FloatingPadService.reload(this)
                toast("已保存")
            }
        )
    }

    /** 弹一个列表选键 */
    private fun pickKey(b: PadButton) {
        val names = PadConfig.KEYS.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("给「${b.label}」绑定按键")
            .setItems(names) { _, which ->
                b.keyCode = PadConfig.KEYS[which].code
                render()
            }
            .show()
    }

    // ---------------- 控件工具 ----------------

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setPadding(0, 30, 0, 12)
    }

    private fun chip(text: String, onClick: () -> Unit) = Button(this).apply {
        this.text = text
        textSize = 13f
        setOnClickListener { onClick() }
    }

    private fun row(vararg views: View) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        views.forEach {
            addView(it, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
        }
    }

    /** 贴图选择格：每行 3 个，当前选中的前面加 ● */
    private fun grid(
        ids: List<String>,
        names: List<String>,
        current: String,
        onPick: (String) -> Unit
    ): LinearLayout {
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var line: LinearLayout? = null
        ids.forEachIndexed { i, id ->
            if (i % 3 == 0) {
                line = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                container.addView(line)
            }
            val label = if (id == current) "● ${names[i]}" else names[i]
            line?.addView(
                chip(label) { onPick(id) },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
        return container
    }

    private fun slider(
        name: String,
        value: Float,
        min: Float,
        max: Float,
        onChange: (Float) -> Unit
    ): LinearLayout {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val label = TextView(this).apply {
            text = "$name：${"%.2f".format(value)}"
            textSize = 15f
            setPadding(0, 30, 0, 8)
        }
        box.addView(label)
        box.addView(
            SeekBar(this).apply {
                this.max = 100
                progress = (((value - min) / (max - min)) * 100).toInt().coerceIn(0, 100)
                setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(bar: SeekBar?, p: Int, fromUser: Boolean) {
                        val v = min + (max - min) * p / 100f
                        label.text = "$name：${"%.2f".format(v)}"
                        onChange(v)
                    }

                    override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(bar: SeekBar?) = Unit
                })
            }
        )
        return box
    }

    private fun isLight(color: Int): Boolean {
        val r = Color.red(color)
        val g = Color.green(color)
        val b = Color.blue(color)
        return (r * 299 + g * 587 + b * 114) / 1000 > 160
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    companion object {
        private val COLOR_NAMES = listOf("左", "下", "上", "右", "白", "黄")
        private val COLOR_VALUES = listOf(
            PadConfig.LANE_LEFT,
            PadConfig.LANE_DOWN,
            PadConfig.LANE_UP,
            PadConfig.LANE_RIGHT,
            0xFFFFFFFF.toInt(),
            0xFFFFD400.toInt()
        )
    }
}
