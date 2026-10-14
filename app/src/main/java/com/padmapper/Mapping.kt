package com.padmapper

import android.content.Context
import android.view.KeyEvent
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** type: TAP | HOLD | SWIPE | WASD | STICK | LOOK | SCROLL
 *  keys: Android keycodes; mouse buttons are 1000 (left), 1001 (right), 1002 (middle).
 *  WASD keys order: up, left, down, right. STICK axis: 0 = left stick, 1 = right stick. */
@Serializable
data class Binding(
    var id: String, var type: String, var keys: List<Int> = emptyList(),
    var x: Float = 0f, var y: Float = 0f, var dx: Float = 0f, var dy: Float = 0f,
    var radius: Float = 150f, var sens: Float = 1f, var axis: Int = 0
)

@Serializable
data class Profile(var name: String, var toggle: List<Int>, val bindings: MutableList<Binding>)

object Store {
    val json = Json { prettyPrint = true; encodeDefaults = true; ignoreUnknownKeys = true }
    private lateinit var f: File
    fun init(c: Context) { f = File(c.filesDir, "profile.json") }
    fun load(): Profile = runCatching { json.decodeFromString<Profile>(f.readText()) }.getOrElse { default() }
    fun save(p: Profile) { f.writeText(json.encodeToString(p)) }
    fun dump(): String = json.encodeToString(load())
    fun default() = Profile(
        "Default",
        listOf(KeyEvent.KEYCODE_CTRL_LEFT, KeyEvent.KEYCODE_SHIFT_LEFT, KeyEvent.KEYCODE_M),
        mutableListOf(
            Binding("move", "WASD", listOf(KeyEvent.KEYCODE_W, KeyEvent.KEYCODE_A, KeyEvent.KEYCODE_S, KeyEvent.KEYCODE_D), x = 400f, y = 1300f, radius = 150f),
            Binding("fire", "TAP", listOf(1000), x = 2400f, y = 1200f),
            Binding("look", "LOOK", x = 1600f, y = 700f, radius = 300f, sens = 1.5f),
            Binding("zoom", "SCROLL", x = 1600f, y = 900f, dy = 300f)
        )
    )
}
