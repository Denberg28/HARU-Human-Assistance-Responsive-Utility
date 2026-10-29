package io.haru.assistant.skills

class HelpSkill : HaruSkill {
    override val name = "Help"

    override fun canHandle(command: String): Boolean {
        val c = command.trim().lowercase()
        return c == "help" || c == "what can you do" || c == "commands"
    }

    override fun execute(command: String): SkillResult = SkillResult(
        "Write a journal entry for today or choose a past date. Star a memorable day and expand older entries by year, month, week, and date. Tasks and reminders are available in Today."
    )
}
