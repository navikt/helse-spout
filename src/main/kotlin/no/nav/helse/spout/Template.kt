package no.nav.helse.spout

import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

internal object Template {

    private val nowPlusRegex = "\\{\\{now\\+(\\d+)([smhdwMy])}}".toRegex()
    private val nowMinusRegex = "\\{\\{now-(\\d+)([smhdwMy])}}".toRegex()

    private val todayPlusRegex = "\\{\\{today\\+(\\d+)([dwMy])}}".toRegex()
    private val todayMinusRegex = "\\{\\{today-(\\d+)([dwMy])}}".toRegex()

    private val units = mapOf(
        "s" to ChronoUnit.SECONDS,
        "m" to ChronoUnit.MINUTES,
        "h" to ChronoUnit.HOURS,
        "d" to ChronoUnit.DAYS,
        "w" to ChronoUnit.WEEKS,
        "M" to ChronoUnit.MONTHS,
        "y" to ChronoUnit.YEARS
    )
    private val MatchResult.antall get() = groupValues[1].toLong()
    private val MatchResult.unit get() = groupValues[2].let { units.getValue(it) }

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
            .replace("{{uuidgen}}", "${UUID.randomUUID()}")
            .replace("{{now}}", "$tidspunkt")
            .replace("{{today}}", "${tidspunkt.toLocalDate()}")
            .replace(nowPlusRegex) { "${tidspunkt.plus(it.antall, it.unit)}" }
            .replace(nowMinusRegex) { "${tidspunkt.minus(it.antall, it.unit)}" }
            .replace(todayPlusRegex) { "${tidspunkt.plus(it.antall, it.unit).toLocalDate()}" }
            .replace(todayMinusRegex) { "${tidspunkt.minus(it.antall, it.unit).toLocalDate()}" }
    }
}