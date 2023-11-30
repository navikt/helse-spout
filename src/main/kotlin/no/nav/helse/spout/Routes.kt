package no.nav.helse.spout

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.UUID
import kotlin.Exception

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val SEND = object {}.javaClass.getResource("/send.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke send.html")
private val KVITTERING = object {}.javaClass.getResource("/kvittering.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke kvittering.html")
private fun Parameters.hent(key: String) = checkNotNull(get(key)?.takeUnless { it.isBlank() }) { "Mangler $key" }
private val objectMapper = jacksonObjectMapper()
private val begrunnelseRegex = "[a-zæøåA-ZÆØÅ0-9 ]{15,100}".toRegex()
private val JsonNode.fødselsnummerOrNull get() = if (hasNonNull("fødselsnummer")) get("fødselsnummer").asText() else null

internal fun Route.spout(
    sender: Sender,
    resolveNavIdent: (call: ApplicationCall) -> String,
    resolveNavn: (call: ApplicationCall) -> String,
    resolveEpost: (call: ApplicationCall) -> String
) {
    staticResources("/static", "static")

    get {
        val mellomnavn = resolveNavn(call).split(" ").lastOrNull() ?: ""
        val temadag = resolveTemadag(call)?: MonthDay.now()
        val html = SEND
            .replace("{{innloggetbruker}}", mellomnavn)
            .replace("{{environment}}", "NAIS_CLUSTER_NAME".env("ingenting"))
            .velgTema(temadag)
        call.respondText(html, ContentType.Text.Html)
    }

    post("/melding") {
        val id = UUID.randomUUID()
        val tidspunkt = LocalDateTime.now()

        val (metadata, melding) = try {
            val navIdent = resolveNavIdent(call)
            val navn = resolveNavn(call)
            val epost = resolveEpost(call)

            val avsender = jacksonObjectMapper().createObjectNode()
                .put("NAVIdent", navIdent)
                .put("navn", navn)
                .put("epost", epost)

            val parameters = call.receiveParameters()

            val begrunnelse = parameters.hent("begrunnelse").replace("=", "\\=")
            check(begrunnelse.length >= 15) { "Litt kort begrunnelse eller? 🤏 MÅ være minst 15 makreller lang!" }

            val input = parameters.hent("json")
            val inputjson = objectMapper.readTree(input)

            val jsonInput = when {
                inputjson.hasNonNull("text") -> objectMapper.readTree(inputjson.path("text").asText())
                inputjson.hasNonNull("json") -> inputjson.path("json")
                else -> error("Ugyldig input: må enten sende json i form av at 'text' er en jsonstring, eller så må 'json' settes til et jsonobjekt")
            }
            check(jsonInput is ObjectNode) {
                "Ugyldig input, jsoninput må sendes som tekst!"
            }

            val json = objectMapper.readTree(Template.resolve(
                input = jsonInput.toString(),
                navIdent = navIdent,
                navn = navn,
                epost = epost,
                tidspunkt = tidspunkt,
                fødselsnummer = jsonInput.fødselsnummerOrNull ?: "n/a",
                begrunnelse = begrunnelse
            )) as ObjectNode

            val fødselsnummer = json.fødselsnummerOrNull?.also {
                check(it.matches("\\d{11}".toRegex())) { "Gyldig 'fødselsnummer' må settes i meldingen"}
            }
            val eventName = json.path("@event_name").asText()
            check(eventName.isNotBlank()) { "Må settes '@event_name' i meldingen" }
            json.replace("@avsender", avsender)

            val (metadata, melding) = sender.send(fødselsnummer, json, tidspunkt, id)
            AuditOgSikkerlogg.logg(
                message = "Sendt melding fra Spout\nMelding:\n\t$melding\nMetadata:\n\t$metadata",
                navIdent = navIdent,
                fødselsnummer = fødselsnummer,
                tidspunkt = tidspunkt,
                eventName = eventName,
                id = id,
                begrunnelse = begrunnelse
            )
            metadata to melding
        } catch (ex: Exception) {
            sikkerlogg.error("Feil ved sending av melding", ex)
            objectMapper.createObjectNode().put("feil", "${ex.message}") to objectMapper.createObjectNode()
        }

        val html = KVITTERING
            .replace("{{json}}", melding.toPrettyString())
            .replace("{{metadata}}", metadata.toPrettyString())
            .replace("{{kibana}}", "https://logs.adeo.no/app/kibana#/discover?_a=(index:'tjenestekall-*',query:(language:lucene,query:'%22${id}%22'))&_g=(time:(from:'$tidspunkt',mode:absolute,to:now))")
            .velgTema(MonthDay.now())

        call.respondText(html, ContentType.Text.Html)
    }
}
