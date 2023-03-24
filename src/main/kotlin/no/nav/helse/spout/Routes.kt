package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.time.LocalDateTime

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val SEND = object {}.javaClass.getResource("/send.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke send.html")
private val KVITTERING = object {}.javaClass.getResource("/kvittering.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke kvittering.html")
private fun Parameters.hent(key: String) = checkNotNull(get(key)?.takeUnless { it.isBlank() }) { "Mangler $key" }
private val objectMapper = jacksonObjectMapper()

internal fun Route.spout(
    sender: Sender,
    navIdent: (call: ApplicationCall) -> String,
    navn: (call: ApplicationCall) -> String,
    epost: (call: ApplicationCall) -> String
) {
    get {
        val mellomnavn = navn(call).split(" ").lastOrNull() ?: ""
        val html = SEND.replace("{{innloggetbruker}}", mellomnavn)
        call.respondText(html, ContentType.Text.Html)
    }

    post("/melding") {
        val (metadata, melding) = try {
            val avsender = jacksonObjectMapper().createObjectNode()
                .put("NAVIdent", navIdent(call))
                .put("navn", navn(call))
                .put("epost", epost(call))

            val parameters = call.receiveParameters()
            val tidspunkt = LocalDateTime.now()
            val input = parameters.hent("json")

            val json = objectMapper.readTree(Template.resolve(
                input = objectMapper.readTree(input).path("text").asText(),
                navIdent = navIdent(call),
                navn = navn(call),
                epost = epost(call),
                tidspunkt = tidspunkt
            )) as ObjectNode

            val fødselsnummer = json.path("fødselsnummer").asText()
            check(fødselsnummer.matches("\\d{11}".toRegex())) { "Gyldig 'fødselsnummer' må settes i meldingen"}
            val eventName = json.path("@event_name").asText()
            check(eventName.isNotBlank()) { "Må settes '@event_name' i meldingen" }
            json.replace("@avsender", avsender)

            val (metadata, melding) = sender.send(fødselsnummer, json, tidspunkt)
            sikkerlogg.info("Sendt melding fra Spout\nMelding:\n\t: $melding\nMetadata:\n\t $metadata")
            metadata to melding
        } catch (ex: Exception) {
            sikkerlogg.error("Feil ved sending av melding", ex)
            objectMapper.createObjectNode().put("feil", "${ex.message}") to objectMapper.createObjectNode()
        }

        val html = KVITTERING
            .replace("{{json}}", melding.toPrettyString())
            .replace("{{metadata}}", metadata.toPrettyString())

        call.respondText(html, ContentType.Text.Html)
    }
}