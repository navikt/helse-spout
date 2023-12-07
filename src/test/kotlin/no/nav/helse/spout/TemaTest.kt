package no.nav.helse.spout

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.time.MonthDay

class TemaTest {
    @Test
    fun `velg et bra tema`() {
        (1..7).januar blir "nyttår"
        (8..31).januar blir "vanlig"
        (1..23).oktober blir "vanlig"
        (24..31).oktober blir "halloween"
        (1..30).november blir "vanlig"
        (1..30).desember blir "jul"
        31.desember blir "nyttår"
    }
    private infix fun MonthDay.blir(theme: String) = Assertions.assertEquals(theme, this.velgTema())
    private infix fun List<MonthDay>.blir(tema: String) = forEach { temadag -> temadag blir tema }
    private val Int.januar get() = MonthDay.of(1, this)
    private val IntRange.januar: List<MonthDay> get() = map { it.januar }
    private val Int.oktober get() = MonthDay.of(10, this)
    private val IntRange.oktober: List<MonthDay> get() = map { it.oktober }
    private val Int.november get() = MonthDay.of(11, this)
    private val IntRange.november: List<MonthDay> get() = map { it.november }
    private val Int.desember get() = MonthDay.of(12, this)
    private val IntRange.desember: List<MonthDay> get() = map { it.desember }
}