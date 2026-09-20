package io.haru.assistant.core

import android.content.Context

enum class CompanionMode(
    val label: String,
    val role: String,
    val priority: String,
    val aiGuidance: String,
    val activationMessage: String,
) {
    NORMAL(
        label = "Normal",
        role = "Everyday companion",
        priority = "Tasks • reminders • notes • voice",
        aiGuidance =
            "Prioritize concise everyday assistance and local actions. " +
                "Do not invent device state or actions.",
        activationMessage =
            "Normal mode active. HARU is ready for everyday assistance.",
    ),
    FLIGHT(
        label = "Flight",
        role = "Flight companion",
        priority = "Checklist • GPS • timers • notes",
        aiGuidance =
            "Prioritize operational awareness and concise aviation support. " +
                "Never invent telemetry, weather, aircraft state, or completed checklist items.",
        activationMessage =
            "Flight mode active. HARU will prioritize checklist, GPS, timers, and flight notes.",
    ),
    TRAVEL(
        label = "Travel",
        role = "Travel companion",
        priority = "Navigation • location • hazards",
        aiGuidance =
            "Prioritize navigation, location context, travel logistics, and hazard awareness. " +
                "Do not claim live location or route state unless HARU actually has it.",
        activationMessage =
            "Travel mode active. HARU will prioritize navigation, location, and hazards.",
    ),
    WORK(
        label = "Work",
        role = "Work companion",
        priority = "Tasks • reminders • notes",
        aiGuidance =
            "Prioritize task clarity, reminders, concise planning, and execution support.",
        activationMessage =
            "Work mode active. HARU will prioritize tasks, reminders, and focused notes.",
    ),
    SAFETY(
        label = "Safety",
        role = "Safety companion",
        priority = "Location • live share • hazards",
        aiGuidance =
            "Prioritize verified location, sharing status, and official hazard information. " +
                "Do not claim emergency contact or rescue actions that were not performed.",
        activationMessage =
            "Safety mode active. HARU will prioritize location, live sharing, and hazards.",
    ),
    REST(
        label = "Rest",
        role = "Low-interruption companion",
        priority = "Essential reminders • quiet status",
        aiGuidance =
            "Keep responses especially brief and avoid unnecessary prompts. " +
                "Essential reminders and explicit user requests still take priority.",
        activationMessage =
            "Rest mode active. HARU will keep interaction minimal while reminders remain available.",
    );

    companion object {
        fun fromStored(value: String?): CompanionMode =
            entries.firstOrNull { it.name == value } ?: NORMAL

        fun fromCommand(command: String): CompanionMode? {
            val clean =
                command
                    .trim()
                    .lowercase()
                    .replace(Regex("[.!?]+$"), "")
                    .replace(Regex("\\s+"), " ")

            val direct =
                Regex(
                    "^(normal|flight|travel|work|safety|rest) mode$"
                ).matchEntire(clean)

            val action =
                Regex(
                    "^(?:start|enter|switch to|set|activate) " +
                        "(?:haru )?(normal|flight|travel|work|safety|rest)" +
                        "(?: mode)?$"
                ).matchEntire(clean)

            val name = direct?.groupValues?.get(1)
                ?: action?.groupValues?.get(1)
                ?: return null

            return entries.firstOrNull {
                it.name.equals(name, ignoreCase = true)
            }
        }
    }
}

class CompanionModeStore(
    context: Context,
) {
    private val preferences =
        context.getSharedPreferences(
            "haru_companion_mode",
            Context.MODE_PRIVATE,
        )

    fun load(): CompanionMode =
        CompanionMode.fromStored(
            preferences.getString(KEY_MODE, null)
        )

    fun save(mode: CompanionMode) {
        preferences.edit()
            .putString(KEY_MODE, mode.name)
            .apply()
    }

    companion object {
        private const val KEY_MODE = "current_mode"
    }
}
