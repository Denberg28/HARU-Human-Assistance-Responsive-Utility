package io.haru.assistant.skills

class CalculatorSkill : HaruSkill {
    override val name = "Calculator"

    private val expression = Regex("""^\s*(?:calculate|compute|what is)?\s*(-?\d+(?:\.\d+)?)\s*([+\-*/x×])\s*(-?\d+(?:\.\d+)?)\s*\??\s*$""", RegexOption.IGNORE_CASE)

    override fun canHandle(command: String): Boolean = expression.matches(command)

    override fun execute(command: String): SkillResult {
        val match = expression.matchEntire(command)
            ?: return SkillResult("I couldn't read that calculation.", false)

        val (aText, op, bText) = match.destructured
        val a = aText.toBigDecimal()
        val b = bText.toBigDecimal()

        var approximate = false
        val result = when (op.lowercase()) {
            "+" -> a + b
            "-" -> a - b
            "*", "x", "×" -> a * b
            "/" -> if (b.signum() != 0) {
                try { a.divide(b) } catch (_: ArithmeticException) {
                    approximate = true
                    a.divide(b, java.math.MathContext.DECIMAL64)
                }
            } else return SkillResult("I can't divide by zero.")
            else -> return SkillResult("That operator isn't supported yet.", false)
        }

        val display = result.stripTrailingZeros().toPlainString()
        return SkillResult("$aText $op $bText ${if (approximate) "≈" else "="} $display")
    }
}
