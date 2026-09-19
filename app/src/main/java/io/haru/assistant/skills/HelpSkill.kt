package io.haru.assistant.skills

class HelpSkill : HaruSkill {
    override val name = "Help"

    override fun canHandle(command: String): Boolean {
        val c = command.trim().lowercase()
        return c == "help" || c == "what can you do" || c == "commands"
    }

    override fun execute(command: String): SkillResult = SkillResult(
        "I can tell the time or date, calculate simple expressions, and respond to basic greetings. Voice, notes, reminders, and connected skills are next."
    )
}
