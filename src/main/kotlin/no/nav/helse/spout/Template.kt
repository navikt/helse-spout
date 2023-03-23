package no.nav.helse.spout

import java.time.LocalDateTime

internal object Template {

    private val plussDagerRegex = "\\{\\{now\\+(\\d+)d}}".toRegex()
    private val minusDagerRegex = "\\{\\{now-(\\d+)d}}".toRegex()

    internal fun resolve(
        input: String,
        navIdent: String,
        navn: String,
        epost: String,
        tidspunkt: LocalDateTime
    ): String {
        return input
            .replace("{{NAVIdent}}", navIdent)
            .replace("{{navn}}", navn)
            .replace("{{epost}}", epost)
            .replace("{{now}}", "$tidspunkt")
            .replace(plussDagerRegex) { "${tidspunkt.plusDays(it.groupValues.last().toLong())}" }
            .replace(minusDagerRegex) { "${tidspunkt.minusDays(it.groupValues.last().toLong())}" }
    }
}