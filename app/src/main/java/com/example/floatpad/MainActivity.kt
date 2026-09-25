package com.example.floatpad

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tip: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(56, 140, 56, 56)
        }
        tip = TextView(this).apply { textSize = 16f }
        box.addView(tip)

        fun add(labelText: String, onClick: () -> Unit) {
            val b = Button(this)
            b.text = labelText
            b.setOnClickListener { onClick() }
            box.addView(b)
        }

        add("1. 授予悬浮窗权限") {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }
        add("2. 开启无障碍服务（注入点击）") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        add("3. 启动悬浮按键") {
            if (canOverlay()) {
                ContextCompat.startForegroundService(
                    this, Intent(this, FloatingPadService::class.java)
                )
            } else {
                Toast.makeText(this, "先给悬浮窗权限", Toast.LENGTH_SHORT).show()
            }
        }
        add("停止悬浮按键") {
            stopService(Intent(this, FloatingPadService::class.java))
        }

        add("编辑按键（数量 / 两层贴图 / 样式 / 上色）") {
            startActivity(Intent(this, PadEditorActivity::class.java))
        }
        add("预设：FNF 四键") { applyPreset(PadConfig.presetFnf4(), "已换成 FNF 四键") }
        add("预设：FNF 六键") { applyPreset(PadConfig.presetFnf6(), "已换成 FNF 六键") }
        add("预设：十字键") { applyPreset(PadConfig.presetDpad(), "已换成十字键") }

        setContentView(box)
    }

    private fun applyPreset(list: List<PadButton>, msg: String) {
        PadConfig.save(this, list)
        FloatingPadService.reload(this)
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        tip.text = buildString {
            append("悬浮窗权限：")
            append(if (canOverlay()) "已开启" else "未开启")
            append("\n无障碍服务：")
            append(if (PadAccessibilityService.isReady()) "已开启" else "未开启")
            append("\n悬浮层：")
            append(if (FloatingPadService.running) "运行中" else "未启动")
            append("\n\n无障碍必须开启，否则按键点不动。")
            append("回游戏后点左上角齿轮可拖动按键。")
        }
    }

    private fun canOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
}
