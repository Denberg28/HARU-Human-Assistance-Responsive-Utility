package io.haru.assistant.skills

class CalculatorSkill : HaruSkill {
    override val name = "Calculator"

    private val expression = Regex("""^\s*(?:calculate|compute|what is)?\s*(-?\d+(?:\.\d+)?)\s*([+\-*/x×])\s*(-?\d+(?:\.\d+)?)\s*\??\s*$""", RegexOption.IGNORE_CASE)

    override fun canHandle(command: String): Boolean = expression.matches(command)

    override fun execute(command: String): SkillResult {
        val match = expression.matchEntire(command)
            ?: return SkillResult("I couldn't read that calculation.", false)

        val (aText, op, bText) = match.destructured
        val a = aText.toDouble()
        val b = bText.toDouble()

        val result = when (op.lowercase()) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "×" -> a * b
            "/" -> if (b != 0.0) a / b else return SkillResult("I can't divide by zero.", false)
            else -> return SkillResult("That operator isn't supported yet.", false)
        }

        val display = if (result % 1.0 == 0.0) result.toLong().toString() else "%.4f".format(result).trimEnd('0').trimEnd('.')
        return SkillResult("$aText $op $bText = $display")
    }
}
