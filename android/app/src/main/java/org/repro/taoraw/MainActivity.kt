package org.repro.taoraw

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.util.TypedValue
import android.widget.ScrollView
import android.widget.TextView

/**
 * The whole Java/Kotlin side, and nothing here is contrived: these are the same
 * calls any tao Android integration must make (wry's generated `WryActivity` /
 * `Rust.kt` makes them from a ProcessLifecycleOwner observer).
 *
 * tao 0.36 renamed these entry points from create/start to
 * onFirstActivityCreate/onStart.
 */
object Rust {
    init {
        System.loadLibrary("taoraw")
    }

    @JvmStatic external fun onFirstActivityCreate()
    @JvmStatic external fun onCreate(activity: Activity)
    @JvmStatic external fun onStart(activity: Activity)
}

class MainActivity : Activity() {
    /** tao 0.36 calls `activity.getId()`; wry's WryActivity carries the same field. */
    @Suppress("unused")
    val id: Int = hashCode()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // DISPLAY ONLY — nothing in the reproduction depends on this.
        //
        // The app creates no tao Window, so the content view is free. Mirroring
        // this process's own logcat on screen means a plain `adb screenrecord`
        // captures the screen and the live log in one video, with no host-side
        // recorder involved. Delete this method and the behaviour is identical.
        showOwnLog()

        Rust.onFirstActivityCreate()
        Rust.onCreate(this)
    }

    override fun onStart() {
        super.onStart()
        Rust.onStart(this)
    }

    private fun showOwnLog() {
        val text = TextView(this).apply {
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
            setTextColor(Color.BLACK)
            setPadding(24, 24, 24, 24)
            typeface = Typeface.MONOSPACE
        }
        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            addView(text)
        }
        setContentView(scroll)

        val ui = Handler(Looper.getMainLooper())
        Thread {
            try {
                val proc = Runtime.getRuntime().exec(
                    arrayOf(
                        "logcat", "-v", "time", "--pid=${Process.myPid()}",
                        "RustStdoutStderr:I", "*:S",
                    )
                )
                proc.inputStream.bufferedReader().forEachLine { line ->
                    // tao redirects the whole process's stdout to logcat, so on
                    // an emulator the GL layer's chatter arrives under the same
                    // tag. Keep only this app's own lines.
                    val start = line.indexOf("[taoraw")
                    if (start < 0) return@forEachLine
                    val shown = line.substring(start)
                    ui.post {
                        text.append(shown + "\n")
                        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
                    }
                }
            } catch (e: Exception) {
                ui.post { text.append("log mirror failed: $e\n") }
            }
        }.apply { isDaemon = true }.start()
    }
}
