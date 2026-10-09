package no.nav.helse.spout

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant

internal class TraceUrlTest {
    @Test
    fun `lager lenke til loggene for tracen med tidsvindu rundt tidspunktet`() {
        val url =
            traceUrl(
                traceId = "83765bca845ce1c0d7ae0227fa8773d1",
                tidspunkt = Instant.parse("2026-10-09T08:36:43.442295475Z"),
                projectId = "tbd-prod-eacd",
            )

        assertEquals(
            "https://console.cloud.google.com/logs/query;query=jsonPayload.trace_id%3D%2283765bca845ce1c0d7ae0227fa8773d1%22;cursorTimestamp=2026-10-09T08:36:43.442295475Z;startTime=2026-10-09T07:36:00Z;endTime=2026-10-09T09:36:00Z?project=tbd-prod-eacd",
            url,
        )
    }
}
