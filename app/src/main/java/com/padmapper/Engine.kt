package com.padmapper

import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

object Engine {
    var profile: Profile = Profile("Default", emptyList(), mutableListOf())
    var inj: Injector = NoopInjector
    var editing = false
    var paused = false           // Scroll Lock toggles: restores normal keyboard/mouse behaviour
    var selected: Binding? = null
    var onToggle: (() -> Unit)? = null

    private val down = HashSet<Int>()
    private val active = HashSet<Int>()
    private val h = Handler(Looper.getMainLooper())
    private var lookEnd = Runnable {}
    private var lx = 0f
    private var ly = 0f
    private const val SCROLL_ID = 31

    fun onKey(e: KeyEvent) = key(e.keyCode, e.action == KeyEvent.ACTION_DOWN, e.repeatCount > 0)

    fun key(k: Int, isDown: Boolean, repeat: Boolean): Boolean {
        if (isDown) down.add(k) else down.remove(k)
        if (isDown && !repeat && profile.toggle.isNotEmpty() && down.containsAll(profile.toggle)) { onToggle?.invoke(); return true }
        if (isDown && !repeat && k == KeyEvent.KEYCODE_SCROLL_LOCK) { paused = !paused; releaseAll(); return true }
        if (editing) {
            val s = selected
            if (isDown && s != null && s.type != "WASD") { s.keys = listOf(k); selected = null; Store.save(profile); return true }
            return false
        }
        if (paused) return false
        var used = false
        profile.bindings.forEachIndexed { i, b ->
            if (k !in b.keys) return@forEachIndexed
            used = true
            when (b.type) {
                "TAP" -> if (isDown && !repeat) { inj.down(i, b.x, b.y); h.postDelayed({ inj.up(i) }, 40) }
                "HOLD" -> if (!isDown) inj.up(i) else if (!repeat) inj.down(i, b.x, b.y)
                "SWIPE" -> if (isDown && !repeat) swipe(i, b.x, b.y, b.dx, b.dy)
                "WASD" -> {
                    var vx = 0f; var vy = 0f
                    if (b.keys[0] in down) vy -= 1f
                    if (b.keys[2] in down) vy += 1f
                    if (b.keys[1] in down) vx -= 1f
                    if (b.keys[3] in down) vx += 1f
                    setStick(i, b, vx, vy)
                }
            }
        }
        return used
    }

    private fun swipe(id: Int, x: Float, y: Float, dx: Float, dy: Float) {
        inj.down(id, x, y)
        val n = 8
        for (s in 1..n) h.postDelayed({ inj.move(id, x + dx * s / n, y + dy * s / n) }, 12L * s)
        h.postDelayed({ inj.up(id) }, 12L * (n + 1))
    }

    private fun setStick(id: Int, b: Binding, vx: Float, vy: Float) {
        val l = hypot(vx, vy)
        if (l < 0.12f) { if (active.remove(id)) inj.up(id); return }
        val s = min(1f, l) / l
        if (active.add(id)) inj.down(id, b.x, b.y)
        inj.move(id, b.x + vx * s * b.radius, b.y + vy * s * b.radius)
    }

    fun onPad(e: MotionEvent) {
        if (editing || paused) return
        profile.bindings.forEachIndexed { i, b ->
            if (b.type != "STICK") return@forEachIndexed
            val (ax, ay) = if (b.axis == 0) MotionEvent.AXIS_X to MotionEvent.AXIS_Y else MotionEvent.AXIS_Z to MotionEvent.AXIS_RZ
            setStick(i, b, e.getAxisValue(ax), e.getAxisValue(ay))
        }
    }

    fun onMouse(dx: Float, dy: Float) {
        if (editing || paused) return
        profile.bindings.forEachIndexed { i, b ->
            if (b.type != "LOOK") return@forEachIndexed
            if (active.add(i)) { lx = b.x; ly = b.y; inj.down(i, lx, ly) }
            lx += dx * b.sens; ly += dy * b.sens
            if (hypot(lx - b.x, ly - b.y) > b.radius) { inj.up(i); lx = b.x; ly = b.y; inj.down(i, lx, ly) }
            else inj.move(i, lx, ly)
            h.removeCallbacks(lookEnd)
            lookEnd = Runnable { if (active.remove(i)) inj.up(i) }
            h.postDelayed(lookEnd, 60)
        }
    }

    fun onScroll(v: Float) {
        if (editing || paused || v == 0f || SCROLL_ID in active) return
        profile.bindings.firstOrNull { it.type == "SCROLL" }?.let { b ->
            active.add(SCROLL_ID)
            swipe(SCROLL_ID, b.x, b.y, 0f, if (v > 0) -abs(b.dy) else abs(b.dy))
            h.postDelayed({ active.remove(SCROLL_ID) }, 130)
        }
    }

    fun releaseAll() { active.toList().forEach { inj.up(it) }; active.clear() }
}
