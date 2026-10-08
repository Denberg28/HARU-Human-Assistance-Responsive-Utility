package io.haru.assistant.skills

class HelpSkill : HaruSkill {
    override val name = "Help"

    override fun canHandle(command: String): Boolean {
        val c = command.trim().lowercase()
        return c == "help" || c == "what can you do" || c == "commands"
    }

    override fun execute(command: String): SkillResult = SkillResult(
        "I can answer questions through your saved AI provider, look up current information when search is available, and handle time, simple arithmetic, tasks and reminders locally. Ask clearly or paste text to review. I cannot guarantee every answer or access files you have not supplied."
    )
}
