package io.haru.assistant.ui

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner

/** Forwards Activity background transitions even while the Compose map remains mounted. */
internal class MapLifecycleBinding(
    private val lifecycle: Lifecycle,
    private val start: () -> Unit,
    private val resume: () -> Unit,
    private val pause: () -> Unit,
    private val stop: () -> Unit,
    private val destroy: () -> Unit,
) : DefaultLifecycleObserver {
    private var started = false
    private var resumed = false
    private var closed = false

    init { lifecycle.addObserver(this) }

    override fun onStart(owner: LifecycleOwner) {
        if (!closed && !started) { start(); started = true }
    }

    override fun onResume(owner: LifecycleOwner) {
        if (!closed && !resumed) { resume(); resumed = true }
    }

    override fun onPause(owner: LifecycleOwner) = pauseIfNeeded()
    override fun onStop(owner: LifecycleOwner) = stopIfNeeded()
    override fun onDestroy(owner: LifecycleOwner) = close()

    private fun pauseIfNeeded() {
        if (resumed) { pause(); resumed = false }
    }

    private fun stopIfNeeded() {
        pauseIfNeeded()
        if (started) { stop(); started = false }
    }

    fun close() {
        if (closed) return
        lifecycle.removeObserver(this)
        stopIfNeeded()
        destroy()
        closed = true
    }
}
