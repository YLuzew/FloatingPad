package com.example.floatpad

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var tip: TextView
    private lateinit var modeRow: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 120, 48, 48)
        }
        tip = TextView(this).apply { textSize = 15f }
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
        add("2. 启用本应用的输入法") {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        add("3. 把当前输入法切成「悬浮按键」") {
            runCatching {
                getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
            }
        }
        add("4. 启动悬浮按键") {
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

        box.addView(
            TextView(this).apply {
                text = "布局（可切换）"
                textSize = 15f
                setPadding(0, 36, 0, 12)
            }
        )
        modeRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        box.addView(modeRow)

        add("编辑按键（绑键 / 贴图 / 大小）") {
            startActivity(Intent(this, PadEditorActivity::class.java))
        }

        setContentView(box)
    }

    private fun renderMode() {
        modeRow.removeAllViews()
        val current = PadConfig.loadMode(this)
        PadMode.entries.forEach { mode ->
            val b = Button(this)
            b.text = if (mode == current) "● ${mode.title}" else mode.title
            b.textSize = 12f
            b.setOnClickListener { applyMode(mode) }
            modeRow.addView(
                b,
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )
        }
    }

    private fun applyMode(mode: PadMode) {
        PadConfig.saveMode(this, mode)
        PadConfig.save(this, PadConfig.modeButtons(mode))
        FloatingPadService.reload(this)
        renderMode()
        Toast.makeText(this, "已切到「${mode.title}」", Toast.LENGTH_SHORT).show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
        renderMode()
    }

    private fun refresh() {
        tip.text = buildString {
            append("悬浮窗：")
            append(if (canOverlay()) "已允许" else "未允许")
            append("\n输入法：")
            append(if (PadInputMethodService.isReady()) "运行中" else "未运行")
            append("\n悬浮层：")
            append(if (FloatingPadService.running) "运行中" else "未启动")
            append("\n\n发按键靠输入法，所以玩之前要把键盘切成「悬浮按键」；")
            append("没绑键的按钮走触摸，需要开无障碍。")
        }
    }

    private fun canOverlay(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
}
