package io.haru.assistant.core

import io.haru.assistant.skills.CalculatorSkill
import io.haru.assistant.skills.GreetingSkill
import io.haru.assistant.skills.HaruSkill
import io.haru.assistant.skills.HelpSkill
import io.haru.assistant.skills.SkillResult
import io.haru.assistant.skills.TimeSkill

class CommandRouter(
    private val skills: List<HaruSkill> = listOf(
        GreetingSkill(),
        TimeSkill(),
        CalculatorSkill(),
        HelpSkill()
    )
) {
    fun route(command: String): SkillResult {
        val clean = command.trim()
        if (clean.isBlank()) return SkillResult("Type a command first.", false)

        val skill = skills.firstOrNull { it.canHandle(clean) }
        return skill?.execute(clean)
            ?: SkillResult(
                "I don't have a local skill for that yet. Try “help”.",
                false
            )
    }
}
