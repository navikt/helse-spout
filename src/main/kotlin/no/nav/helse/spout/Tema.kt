package no.nav.helse.spout

import io.ktor.server.application.*
import java.time.Month
import java.time.MonthDay
import java.time.format.DateTimeFormatter

internal fun resolveTemadag(call: ApplicationCall): MonthDay? {
    val kanskjeDatoString = call.parameters["temadag"] ?: return null
    return try {
        MonthDay.parse(kanskjeDatoString, DateTimeFormatter.ofPattern("MM-dd"))
    } catch (e: Exception) {
        null
    }
}

internal fun MonthDay.velgTema(): String = when {
    month == Month.DECEMBER && dayOfMonth != 31 -> "jul"
    month == Month.DECEMBER && dayOfMonth == 31 -> "nyttår"
    month == Month.OCTOBER && dayOfMonth > 23-> "halloween"
    month == Month.JANUARY && dayOfMonth < 8 -> "nyttår"
    else -> "vanlig"
}
internal fun String.velgTema(temadag: MonthDay) = this.replace("helt_vanlig.css", "helt_${temadag.velgTema()}.css")