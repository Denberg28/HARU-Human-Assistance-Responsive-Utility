package io.haru.assistant.skills

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class TimeSkill : HaruSkill {
    override val name = "Time"

    override fun canHandle(command: String): Boolean {
        val c = command.trim().lowercase().replace(Regex("""\s+"""), " ").trimEnd('?', '.', '!')
        return c in setOf("what time is it", "what is the time", "current time", "time", "what date is it", "what is the date", "what date is today", "today", "date", "today's date")
    }

    override fun execute(command: String): SkillResult {
        val now = LocalDateTime.now()
        val wantsDate = command.lowercase().contains("date") || command.trim().equals("today", true)
        val value = if (wantsDate) {
            now.format(DateTimeFormatter.ofPattern("EEEE, MMMM d, yyyy"))
        } else {
            now.format(DateTimeFormatter.ofPattern("h:mm a"))
        }
        return SkillResult(value)
    }
}
