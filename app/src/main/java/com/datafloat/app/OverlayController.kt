package com.datafloat.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max

/**
 * Draws the draggable counter with a TYPE_APPLICATION_OVERLAY window. Plain Views (not Compose) keep
 * the overlay light on battery and memory. Tap = cycle Daily/Session/Monthly, drag = move.
 */
class OverlayController(
    private val ctx: Context,
    private val onMoved: (x: Int, y: Int) -> Unit,
    private val onTap: () -> Unit,
) {
    private val wm = ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var view: TextView? = null
    private var lp: WindowManager.LayoutParams? = null
    private var settings = AppSettings()
    private var styleKey: List<Any> = emptyList()
    private var dragging = false

    private fun dp(v: Int): Int = (v * ctx.resources.displayMetrics.density).toInt()

    fun update(s: AppSettings, text: String) {
        settings = s
        if (!s.overlayVisible || !Settings.canDrawOverlays(ctx)) {
            hide()
            return
        }
        val v = view ?: create() ?: return
        var changed = false
        val key = listOf(s.textSizeSp, s.textColor, s.bgColor, s.opacity, s.bubblePaddingDp, s.cornerRadiusDp, s.showBackground)
        if (key != styleKey) {
            styleKey = key
            applyStyle(v, s)
            changed = true
        }
        if (v.text.toString() != text) {
            v.text = text
            changed = true
        }
        if (changed || s.overlayX < 0 || s.overlayY < 0) {
            if (!dragging) reposition()
        }
    }

    fun reposition() {
        val v = view ?: return
        val p = lp ?: return
        v.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED)
        val (sw, sh) = screenSize()
        val w = v.measuredWidth
        val h = v.measuredHeight
        val s = settings
        val x = when (s.overlayX) {
            Pos.LEFT -> dp(8)
            Pos.RIGHT -> sw - w - dp(8)
            else -> s.overlayX
        }
        val y = when (s.overlayY) {
            Pos.TOP -> dp(36)
            Pos.BOTTOM -> sh - h - dp(56)
            else -> s.overlayY
        }
        val nx = x.coerceIn(0, max(0, sw - w))
        val ny = y.coerceIn(0, max(0, sh - h))
        if (nx != p.x || ny != p.y) {
            p.x = nx
            p.y = ny
            try { wm.updateViewLayout(v, p) } catch (e: Exception) {}
        }
    }

    fun hide() {
        view?.let { try { wm.removeView(it) } catch (e: Exception) {} }
        view = null
        lp = null
        styleKey = emptyList()
    }

    fun destroy() = hide()

    @SuppressLint("ClickableViewAccessibility")
    private fun create(): TextView? {
        val tv = TextView(ctx).apply {
            gravity = Gravity.CENTER
            setSingleLine(true)
            typeface = Typeface.DEFAULT_BOLD
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START }

        val slop = ViewConfiguration.get(ctx).scaledTouchSlop
        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        tv.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (e.rawX - downX).toInt()
                    val dy = (e.rawY - downY).toInt()
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val (sw, sh) = screenSize()
                        params.x = (startX + dx).coerceIn(0, max(0, sw - tv.width))
                        params.y = (startY + dy).coerceIn(0, max(0, sh - tv.height))
                        try { wm.updateViewLayout(tv, params) } catch (e: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        dragging = false
                        onMoved(params.x, params.y)
                    } else {
                        onTap()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> { dragging = false; true }
                else -> false
            }
        }

        return try {
            wm.addView(tv, params)
            view = tv
            lp = params
            tv
        } catch (e: Exception) {
            null // e.g. permission revoked between the check and addView
        }
    }

    private fun applyStyle(v: TextView, s: AppSettings) {
        v.setTextColor(s.textColor)
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, s.textSizeSp.toFloat())
        val pad = dp(s.bubblePaddingDp)
        v.setPadding(pad * 3 / 2, pad, pad * 3 / 2, pad)
        if (s.showBackground) {
            v.background = GradientDrawable().apply {
                setColor(s.bgColor)
                cornerRadius = dp(s.cornerRadiusDp).toFloat()
            }
            v.setShadowLayer(0f, 0f, 0f, 0)
        } else {
            v.background = null
            v.setShadowLayer(6f, 0f, 0f, 0xFF000000.toInt()) // keep text legible without a bubble
        }
        v.alpha = (s.opacity / 100f).coerceIn(0.15f, 1f)
    }

    private fun screenSize(): Pair<Int, Int> =
        if (Build.VERSION.SDK_INT >= 30) {
            val b = wm.maximumWindowMetrics.bounds
            b.width() to b.height()
        } else {
            val dm = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels to dm.heightPixels
        }
}
