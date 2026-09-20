package io.haru.assistant.core

object HaruFaces {
    fun forMood(mood: HaruMood): String =
        when (mood) {
            HaruMood.IDLE -> "₍^. .^₎⟆"
            HaruMood.HAPPY -> "₍^ >ヮ<^₎♡"
            HaruMood.LISTENING -> "₍^. ̫ .^₎♫"
            HaruMood.THINKING -> "₍^. .^₎?"
            HaruMood.WORKING -> "₍^•⩊•^₎⚙"
            HaruMood.CONFUSED -> "₍^. .^₎՞"
            HaruMood.ALERT -> "₍⊙ᆺ⊙₎!"
            HaruMood.SLEEPY -> "₍^-.-^₎ zZ"
        }

    fun idleRotation(
        hourOfDay: Int,
        step: Long,
    ): String {
        val moods =
            if (hourOfDay in 22..23 || hourOfDay in 0..4) {
                listOf(
                    HaruMood.SLEEPY,
                    HaruMood.IDLE,
                    HaruMood.SLEEPY,
                    HaruMood.HAPPY,
                )
            } else {
                listOf(
                    HaruMood.IDLE,
                    HaruMood.HAPPY,
                    HaruMood.LISTENING,
                    HaruMood.IDLE,
                    HaruMood.WORKING,
                    HaruMood.HAPPY,
                )
            }

        val index =
            Math.floorMod(
                step,
                moods.size.toLong(),
            ).toInt()

        return forMood(moods[index])
    }
}
