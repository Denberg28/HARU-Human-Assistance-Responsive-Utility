package io.haru.assistant.onlineai

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class GeminiAgentCatalogTest {
    @Test
    fun cachedCatalogKeepsOnlyNewestStableFlashAndFlashLite() {
        val context = RuntimeEnvironment.getApplication()
        val prefs =
            context.getSharedPreferences(
                "haru_online_ai",
                Context.MODE_PRIVATE,
            )

        val catalog =
            JSONArray()
                .put(model("gemini-2.5-flash", "Gemini 2.5 Flash"))
                .put(model("gemini-3.6-flash", "Gemini 3.6 Flash"))
                .put(model("gemini-3.8-flash", "Gemini 3.8 Flash"))
                .put(model("gemini-3.1-flash-lite", "Gemini 3.1 Flash-Lite"))
                .put(model("gemini-3.5-flash-lite", "Gemini 3.5 Flash-Lite"))
                .put(model("gemini-3-flash-preview", "Gemini 3 Flash Preview"))
                .put(model("gemini-3.8-flash-tts", "Gemini 3.8 Flash TTS"))

        prefs.edit()
            .putString("gemini_catalog", catalog.toString())
            .commit()

        val models =
            AndroidOnlineAiManager(context).geminiModels()

        assertEquals(
            listOf(
                "gemini-3.8-flash",
                "gemini-3.5-flash-lite",
            ),
            models.map { it.id },
        )
    }

    private fun model(
        id: String,
        label: String,
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("label", label)
}
