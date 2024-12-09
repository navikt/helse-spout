package no.nav.helse.spout

import io.ktor.server.application.*
import org.slf4j.LoggerFactory
import java.io.File
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
    month in setOf(Month.APRIL, Month.MAY) -> "vår"
    month in setOf(Month.JUNE, Month.JULY, Month.AUGUST) -> "sommer"
    else -> "vanlig"
}
internal fun String.velgTema(temadag: MonthDay) = this.replace("tema.css", "helt_${temadag.velgTema()}.css")

internal fun testAlleTema(classLoader: ClassLoader) {
    val log = LoggerFactory.getLogger("tema")
    val staticResource = classLoader.getResource("static")
    log.info("staticResource=$staticResource")
    val staticResourcePath = staticResource?.path
    log.info("staticResourcePath=$staticResourcePath")
    if (staticResourcePath == null) return
    val file = File(staticResourcePath)
    val files = file.listFiles()
    if (files == null) {
        log.info("lista med filer var null")
        return
    }
    log.info("lista med filer var ${files.joinToString { it.name }}")
}
internal fun alleTema(classLoader: ClassLoader): Set<String> = classLoader.getResource("static")?.path?.let { statiskRessurs ->
    File(statiskRessurs).listFiles()
        ?.filter { fil -> fil.name.endsWith(".css") && fil.name.startsWith("helt_") }
        ?.map { temaFil -> temaFil.name.substring(5).dropLast(4) }
        ?.toSet()
}?: emptySet()