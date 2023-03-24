package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.convertValue
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import no.nav.helse.rapids_rivers.JsonMessage
import org.slf4j.LoggerFactory
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
        call.respondText(SEND, ContentType.Text.Html)
    }

    post("/melding") {
        val avsender = jacksonObjectMapper().createObjectNode()
            .put("NAVIdent", navIdent(call))
            .put("navn", navn(call))
            .put("epost", epost(call))

        val parameters = call.receiveParameters()
        val fødselsnummer = parameters.hent("fodselsnummer")
        val tidspunkt = LocalDateTime.now()

        val json = objectMapper.readTree(Template.resolve(
            input = parameters.hent("json"),
            navIdent = navIdent(call),
            navn = navn(call),
            epost = epost(call),
            tidspunkt = tidspunkt
        )) as ObjectNode

        val inputData = objectMapper.convertValue<Map<String, Any>>(json)
            .filterNot { (key, _) -> key in setOf("@event_name", "fødselsnummer", "aktørId", "@id", "@opprettet", "@avsender") }
        val påkrevdData = mapOf(
            "fødselsnummer" to fødselsnummer,
            "aktørId" to parameters.hent("aktorId"),
            "@avsender" to mapOf(
                "NAVident" to navIdent(call),
                "navn" to navn(call),
                "epost" to epost(call)
            )
        )
        val packet = JsonMessage.newMessage(parameters.hent("event_name"), inputData + påkrevdData)
        val (metadata, melding) = sender.send(fødselsnummer, packet)
        sikkerlogg.info("Sendt melding fra Spout\nMelding:\n\t: $melding\nMetadata:\n\t $metadata")

        val html = KVITTERING
            .replace("{{json}}", objectMapper.readTree(melding.toJson()).toPrettyString())
            .replace("{{metadata}}", metadata.toPrettyString())

        call.respondText(html, ContentType.Text.Html)
    }
}