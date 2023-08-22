package no.nav.helse.spout

import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

internal object AuditOgSikkerlogg {

    internal fun logg(message: String, navIdent: String, fødselsnummer: String?, tidspunkt: LocalDateTime, eventName: String, id: UUID, begrunnelse: String) {
        if (fødselsnummer == null) return sikkerlogg.info(message)
        val auditMelding = auditMelding(navIdent, fødselsnummer, tidspunkt, eventName, id, begrunnelse)
        sikkerlogg.info("$message\nAudit:\n\t$auditMelding")
        auditlogg.info(auditMelding)
    }

    internal fun auditMelding(navIdent: String, fødselsnummer: String, tidspunkt: LocalDateTime, eventName: String, id: UUID, begrunnelse: String): String {
        val end = ZonedDateTime.of(tidspunkt, ZoneId.of("Europe/Oslo")).toInstant().toEpochMilli()
        return "CEF:0|Vedtaksløsning for sykepenger|Spout|1.0|audit:update|Sporingslogg|INFO|end=$end duid=$fødselsnummer suid=$navIdent request=$eventName sproc=$id msg=$begrunnelse"
    }

    private val auditlogg = LoggerFactory.getLogger("auditLogger")
    private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
}