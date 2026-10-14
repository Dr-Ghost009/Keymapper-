package com.padmapper

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.GestureDetector
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import rikka.shizuku.Shizuku
import kotlin.math.hypot

class MapperService : AccessibilityService() {
    companion object { var instance: MapperService? = null }

    private lateinit var wm: WindowManager
    private var cap: CaptureView? = null
    private var dash: DashView? = null

    override fun onServiceConnected() {
        instance = this
        Store.init(this)
        wm = getSystemService(WindowManager::class.java)
        cap = CaptureView(this).also { wm.addView(it, lp(focusable = true)) }
        dash = DashView(this).also { it.visibility = View.GONE; wm.addView(it, lp(focusable = false)) }
        Engine.onToggle = { toggle() }
        reload()
    }

    fun reload() {
        Engine.profile = Store.load()
        Engine.inj = runCatching<Injector> {
            if (Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) ShizukuInjector()
            else A11yInjector(this)
        }.getOrDefault(A11yInjector(this))
        dash?.invalidate()
    }

    private fun toggle() {
        Engine.editing = !Engine.editing
        Engine.releaseAll()
        dash?.visibility = if (Engine.editing) View.VISIBLE else View.GONE
        if (Engine.editing) cap?.releasePointerCapture() else { cap?.requestFocus(); cap?.requestPointerCapture() }
    }

    // Overlay is never touchable so injected touches reach the app underneath; the dashboard is touchable only while visible.
    private fun lp(focusable: Boolean) = WindowManager.LayoutParams(
        -1, -1, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        (if (focusable) WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE else WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) or
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    )

    override fun onKeyEvent(event: KeyEvent): Boolean = Engine.onKey(event)
    override fun onAccessibilityEvent(e: AccessibilityEvent?) {}
    override fun onInterrupt() { Engine.releaseAll() }
    override fun onDestroy() { instance = null; cap?.let { wm.removeView(it) }; dash?.let { wm.removeView(it) }; super.onDestroy() }
}

/** Invisible, focusable view: owns pointer capture (raw mouse) and receives gamepad sticks. */
class CaptureView(c: Context) : View(c) {
    private var prev = 0
    init {
        isFocusable = true; isFocusableInTouchMode = true
        setOnCapturedPointerListener { _, e ->
            val s = e.buttonState; val ch = s xor prev
            for (bit in 0..2) { val m = 1 shl bit; if (ch and m != 0) Engine.key(1000 + bit, s and m != 0, false) }
            prev = s
            when (e.actionMasked) {
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> Engine.onMouse(e.x, e.y)
                MotionEvent.ACTION_SCROLL -> Engine.onScroll(e.getAxisValue(MotionEvent.AXIS_VSCROLL))
            }
            true
        }
        setOnGenericMotionListener { _, e ->
            if (e.source and InputDevice.SOURCE_JOYSTICK != 0 && e.action == MotionEvent.ACTION_MOVE) { Engine.onPad(e); true } else false
        }
    }
    override fun onWindowFocusChanged(hasWindowFocus: Boolean) {
        super.onWindowFocusChanged(hasWindowFocus)
        if (hasWindowFocus && !Engine.editing) { requestFocus(); requestPointerCapture() }
    }
}

/** Edit dashboard: drag markers, tap one then press a key to bind it, long-press empty space to add a TAP. */
class DashView(c: Context) : View(c) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = 36f }
    private var drag: Binding? = null
    private val gd = GestureDetector(c, object : GestureDetector.SimpleOnGestureListener() {
        override fun onLongPress(e: MotionEvent) {
            if (hit(e.x, e.y) == null) {
                Engine.profile.bindings.add(Binding("btn${Engine.profile.bindings.size}", "TAP", x = e.x, y = e.y))
                Store.save(Engine.profile); invalidate()
            }
        }
    })
    init { setBackgroundColor(0x88000000.toInt()) }

    private fun hit(x: Float, y: Float) =
        Engine.profile.bindings.minByOrNull { hypot(it.x - x, it.y - y) }?.takeIf { hypot(it.x - x, it.y - y) < 90 }

    override fun onDraw(c: Canvas) {
        Engine.profile.bindings.forEach { b ->
            val r = if (b.type in listOf("WASD", "STICK", "LOOK")) b.radius else 50f
            p.style = Paint.Style.STROKE; p.strokeWidth = 6f
            p.color = if (b === Engine.selected) Color.YELLOW else Color.CYAN
            c.drawCircle(b.x, b.y, r, p)
            p.style = Paint.Style.FILL
            c.drawText(b.type + " " + b.keys.joinToString { KeyEvent.keyCodeToString(it).removePrefix("KEYCODE_") }, b.x - r, b.y + r + 40, p)
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { drag = hit(e.x, e.y); Engine.selected = drag; invalidate() }
            MotionEvent.ACTION_MOVE -> drag?.let { it.x = e.x; it.y = e.y; invalidate() }
            MotionEvent.ACTION_UP -> if (drag != null) Store.save(Engine.profile)
        }
        return true
    }
}
