package io.haru.assistant.ui

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [26, 36])
class MapLifecycleBindingTest {
    private class Owner : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this)
    }

    @Test fun backgroundPausesMapWithoutDisposingCompositionAndReturnResumesIt() {
        val owner = Owner()
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        val events = mutableListOf<String>()
        val binding = MapLifecycleBinding(owner.lifecycle,
            { events.add("start") }, { events.add("resume") }, { events.add("pause") },
            { events.add("stop") }, { events.add("destroy") })
        assertEquals(listOf("start", "resume"), events)
        owner.lifecycle.currentState = Lifecycle.State.CREATED
        assertEquals(listOf("start", "resume", "pause", "stop"), events)
        owner.lifecycle.currentState = Lifecycle.State.RESUMED
        binding.close()
        owner.lifecycle.currentState = Lifecycle.State.DESTROYED
        binding.close()
        assertEquals(listOf("start", "resume", "pause", "stop", "start", "resume",
            "pause", "stop", "destroy"), events)
    }
}
