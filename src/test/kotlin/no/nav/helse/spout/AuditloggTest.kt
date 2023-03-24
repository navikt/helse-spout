package no.nav.helse.spout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.LocalDateTime
import java.util.UUID

internal class AuditloggTest {

    @Test
    fun `logger audit på rett format`() {
        val tidspunkt = LocalDateTime.parse("2023-03-23T23:00:00.000000")
        val id = UUID.fromString("5beb1241-d2db-436e-a5e5-8f7712bdeca3")

        val melding = AuditOgSikkerlogg.auditMelding(
            navIdent = "X123456",
            fødselsnummer = "12345678910",
            tidspunkt = tidspunkt,
            eventName = "et_kult_event",
            id = id,
            begrunnelse = "Skal bare gjøre noen greier"
        )

        val expected = "CEF:0|Spout|auditLog|1.0|audit:update|Sporingslogg|INFO|end=1679608800 duid=12345678910 suid=X123456 request=et_kult_event sproc=5beb1241-d2db-436e-a5e5-8f7712bdeca3 msg=Skal bare gjøre noen greier"

        assertEquals(expected, melding)
    }
}