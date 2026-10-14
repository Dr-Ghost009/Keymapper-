package com.padmapper

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.PointF
import android.os.IBinder
import android.os.SystemClock
import android.view.InputDevice
import android.view.InputEvent
import android.view.MotionEvent
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper
import java.lang.reflect.Method

/** Pointer ids must be 0..31. */
interface Injector {
    fun down(id: Int, x: Float, y: Float)
    fun move(id: Int, x: Float, y: Float)
    fun up(id: Int)
}

object NoopInjector : Injector {
    override fun down(id: Int, x: Float, y: Float) {}
    override fun move(id: Int, x: Float, y: Float) {}
    override fun up(id: Int) {}
}

/** Real multi-touch through IInputManager.injectInputEvent, called with shell privileges via Shizuku. */
class ShizukuInjector : Injector {
    private val ptr = LinkedHashMap<Int, PointF>()
    private var downTime = 0L
    private val im: Any
    private val inject: Method

    init {
        HiddenApiBypass.addHiddenApiExemptions("L")
        val binder = ShizukuBinderWrapper(SystemServiceHelper.getSystemService("input"))
        im = Class.forName("android.hardware.input.IInputManager\$Stub")
            .getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
        inject = Class.forName("android.hardware.input.IInputManager")
            .getMethod("injectInputEvent", InputEvent::class.java, Int::class.javaPrimitiveType)
    }

    private fun emit(action: Int) {
        val ids = ptr.keys.toList(); val n = ids.size
        if (n == 0) return
        val props = Array(n) { MotionEvent.PointerProperties().apply { id = ids[it]; toolType = MotionEvent.TOOL_TYPE_FINGER } }
        val crd = Array(n) { MotionEvent.PointerCoords().apply { x = ptr[ids[it]]!!.x; y = ptr[ids[it]]!!.y; pressure = 1f; size = 1f } }
        val e = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, n, props, crd,
            0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0)
        runCatching { inject.invoke(im, e, 0) }
        e.recycle()
    }

    @Synchronized override fun down(id: Int, x: Float, y: Float) {
        if (ptr.containsKey(id)) return
        val first = ptr.isEmpty()
        ptr[id] = PointF(x, y)
        if (first) { downTime = SystemClock.uptimeMillis(); emit(MotionEvent.ACTION_DOWN) }
        else emit(MotionEvent.ACTION_POINTER_DOWN or (ptr.keys.toList().indexOf(id) shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
    }

    @Synchronized override fun move(id: Int, x: Float, y: Float) {
        ptr[id]?.set(x, y) ?: return
        emit(MotionEvent.ACTION_MOVE)
    }

    @Synchronized override fun up(id: Int) {
        val idx = ptr.keys.toList().indexOf(id)
        if (idx < 0) return
        emit(if (ptr.size == 1) MotionEvent.ACTION_UP else MotionEvent.ACTION_POINTER_UP or (idx shl MotionEvent.ACTION_POINTER_INDEX_SHIFT))
        ptr.remove(id)
    }
}

/** No-root fallback: taps and swipes only (one gesture per release). Joysticks need Shizuku. */
class A11yInjector(private val s: AccessibilityService) : Injector {
    private val a = HashMap<Int, PointF>(); private val b = HashMap<Int, PointF>(); private val t = HashMap<Int, Long>()
    override fun down(id: Int, x: Float, y: Float) { a[id] = PointF(x, y); b[id] = PointF(x, y); t[id] = SystemClock.uptimeMillis() }
    override fun move(id: Int, x: Float, y: Float) { b[id] = PointF(x, y) }
    override fun up(id: Int) {
        val p = a.remove(id) ?: return; val q = b.remove(id) ?: return
        val d = maxOf(40L, SystemClock.uptimeMillis() - (t.remove(id) ?: 0L))
        val path = Path().apply { moveTo(p.x, p.y); if (p != q) lineTo(q.x, q.y) }
        s.dispatchGesture(GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path, 0, d)).build(), null, null)
    }
}
