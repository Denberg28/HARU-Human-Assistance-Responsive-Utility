package io.haru.assistant.skills

data class SkillResult(
    val message: String,
    val success: Boolean = true
)

interface HaruSkill {
    val name: String
    fun canHandle(command: String): Boolean
    fun execute(command: String): SkillResult
}
