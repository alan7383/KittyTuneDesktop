import com.alananasss.kittytune.ui.pet.PetStreak
import com.alananasss.kittytune.ui.pet.PetStreak.Mood
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** The cat's daily streak: fed days in a row, a missed day that can be restored, and the cat leaving. */
class PetStreakTest {

    private val today = LocalDate.of(2026, 11, 23)
    private val goal = PetStreak.DAILY_GOAL_MS

    private fun fed(vararg daysAgo: Int): Map<LocalDate, Long> = daysAgo.associate { today.minusDays(it.toLong()) to goal }

    @Test
    fun aNewCatHasNoStreak() {
        val s = PetStreak.compute(emptyMap(), emptySet(), today)
        assertEquals(Mood.NEW, s.mood)
        assertEquals(0, s.days)
    }

    @Test
    fun feedingTodayCountsTheWholeRun() {
        val s = PetStreak.compute(fed(0, 1, 2, 3), emptySet(), today)
        assertEquals(Mood.FED, s.mood)
        assertEquals(4, s.days)
    }

    @Test
    fun todayNotFedYetKeepsYesterdaysStreakAlive() {
        val s = PetStreak.compute(fed(1, 2) + (today to goal / 2), emptySet(), today)
        assertEquals(Mood.HUNGRY, s.mood)
        assertEquals(2, s.days)
        assertEquals(0.5f, s.goalProgress, 0.001f)
    }

    @Test
    fun aMissedDayCanBeRestored() {
        val s = PetStreak.compute(fed(2, 3, 4), emptySet(), today)
        assertEquals(Mood.RESTORABLE, s.mood)
        assertEquals(3, s.days)
        assertEquals(1, s.missedDays)
        assertTrue(s.canRestore)
        val restored = PetStreak.daysToRestore(s, today).toSet()
        assertEquals(setOf(today.minusDays(1)), restored)
        // Once restored the streak is whole again, still waiting for today's music.
        val after = PetStreak.compute(fed(2, 3, 4), restored, today)
        assertEquals(Mood.HUNGRY, after.mood)
        assertEquals(4, after.days)
    }

    @Test
    fun noRestoresLeftMeansNoRestore() {
        val s = PetStreak.compute(fed(2), emptySet(), today, restoresLeft = 0)
        assertEquals(Mood.RESTORABLE, s.mood)
        assertFalse(s.canRestore)
    }

    @Test
    fun tooLongWithoutMusicAndTheCatLeaves() {
        val s = PetStreak.compute(fed(4, 5, 6), emptySet(), today)
        assertEquals(Mood.GONE, s.mood)
        assertEquals(0, s.days)
        // Music today brings it back, with a fresh streak.
        val back = PetStreak.compute(fed(0, 4, 5, 6), emptySet(), today)
        assertEquals(Mood.FED, back.mood)
        assertEquals(1, back.days)
    }

    @Test
    fun aDayBelowTheGoalIsNotFed() {
        val s = PetStreak.compute(mapOf(today to goal - 1, today.minusDays(1) to goal), emptySet(), today)
        assertEquals(Mood.HUNGRY, s.mood)
        assertEquals(1, s.days)
    }
}
