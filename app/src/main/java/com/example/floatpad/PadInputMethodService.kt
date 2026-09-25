package com.example.floatpad

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo

/**
 * 只干一件事：以输入法身份把键盘事件送进当前游戏。
 * 按钮在悬浮层上，这里不需要显示任何键盘界面。
 */
class PadInputMethodService : InputMethodService() {

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        // 不需要候选栏，也不弹键盘
        setCandidatesViewShown(false)
    }

    /** 键盘视图给个高度极小的透明 View，避免挡住游戏 */
    override fun onCreateInputView(): View {
        return View(this).apply { layoutParams = android.view.ViewGroup.LayoutParams(1, 1) }
    }

    /** 发一次按键：按下 + 抬起 */
    fun sendKey(keyCode: Int): Boolean {
        val ic = currentInputConnection ?: return false
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0))
        return true
    }

    companion object {
        @Volatile
        var instance: PadInputMethodService? = null
            private set

        fun isReady(): Boolean = instance != null
    }
}
