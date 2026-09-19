package io.haru.assistant.skills

class GreetingSkill : HaruSkill {
    override val name = "Greeting"

    override fun canHandle(command: String): Boolean {
        val c = command.trim().lowercase()
        return c in setOf("hi", "hello", "hey", "haru", "hello haru", "good morning", "good afternoon", "good evening")
    }

    override fun execute(command: String): SkillResult = SkillResult("Ready. What can I help you with?")
}
