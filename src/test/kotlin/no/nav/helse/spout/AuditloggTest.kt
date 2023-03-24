package no.nav.helse.spout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime

internal class AuditloggTest {

    @Test
    fun `logger audit på rett format`() {
        val tidspunkt = LocalDateTime.parse("2023-03-23T23:00:00.000000")

        val melding = AuditOgSikkerlogg.auditMelding(
            navIdent = "X123456",
            fødselsnummer = "12345678910",
            tidspunkt = tidspunkt,
            eventName = "et_kult_event"
        )

        val expected = "CEF:0|Spout|auditLog|1.0|audit:update|Sporingslogg|INFO|end=1679608800 duid=12345678910 suid=X123456 request=et_kult_event"

        assertEquals(expected, melding)
    }
}