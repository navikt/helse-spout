package no.nav.helse.spout

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.MonthDay
import java.util.*
import no.nav.helse.spout.SendtMelding.Companion.kvittering
import no.nav.helse.spout.SendtMelding.Companion.somSendtMelding
import no.nav.sykepenger.libs.logging.navngittLogger

private val logger = navngittLogger("no.nav.helse.spout.Routes")
private val SEND = object {}.javaClass.getResource("/send.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke send.html")
private val KVITTERING = object {}.javaClass.getResource("/kvittering.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke kvittering.html")
private fun Parameters.hent(key: String) = checkNotNull(get(key)?.takeUnless { it.isBlank() }) { "Mangler $key" }
private val objectMapper = jacksonObjectMapper()
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
        val temadag = resolveTemadag(call) ?: MonthDay.now()
        val html = SEND
            .replace("{{innloggetbruker}}", mellomnavn)
            .replace("{{environment}}", "NAIS_CLUSTER_NAME".env("ingenting"))
            .velgTema(temadag)
        call.respondText(html, ContentType.Text.Html)
    }

    get("/tema") {
        call.respondText("""{
            "tema": [${alleTema(environment.classLoader).joinToString(",") { tema -> "\"$tema\"" }}]
        }""".trimMargin(), ContentType.Application.Json)
    }

    post("/melding") {
        val spoutRequest = try { spoutRequest(resolveNavIdent, resolveNavn, resolveEpost) } catch (ex: Exception) {
            spoutResponse(listOf(ex.somSendtMelding))
            logger.error("Feil i request til Spout", ex)
            return@post
        }

        val sendtMeldinger = spoutRequest.map { melding ->
            sendÉnMelding(sender, spoutRequest.avsender, spoutRequest.begrunnelse, melding)
        }

        logger.info("Behandlet meldinger", "antall" to sendtMeldinger.size.toString())

        spoutResponse(sendtMeldinger)
    }
}

private suspend fun RoutingContext.spoutRequest(resolveNavIdent: (call: ApplicationCall) -> String, resolveNavn: (call: ApplicationCall) -> String, resolveEpost: (call: ApplicationCall) -> String): SpoutRequest{
    val navIdent = resolveNavIdent(call)
    val navn = resolveNavn(call)
    val epost = resolveEpost(call)
    val parameters = call.receiveParameters()

    val begrunnelse = settSammenEnBegrunnelse(parameters.hent("begrunnelse").replace("=", "\\="), parameters["issueLink"]?.replace("=", "\\="))
    check(begrunnelse.length >= 15) { "Litt kort begrunnelse eller? 🤏 MÅ være minst 15 makreller lang!" }

    val avsender = Avsender(navIdent, navn, epost, begrunnelse)

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

    return SpoutRequest(avsender, begrunnelse, jsonInput)
}

fun settSammenEnBegrunnelse(begrunnelse: String, issueLink: String?) =
    if (issueLink == null || issueLink.isBlank()) begrunnelse
    else "$begrunnelse - se $issueLink"

private suspend fun RoutingContext.spoutResponse(sendteMeldinger: List<SendtMelding>) {
    val from = sendteMeldinger.minOf { it.tidspunkt }
    val query = sendteMeldinger.joinToString("%20OR%20") { "%22${it.id}%22" }
    val json = sendteMeldinger.kvittering { it.melding }
    val metadata = sendteMeldinger.kvittering { it.metadata }
    val projectId = "GOOGLE_CLOUD_PROJECT".env("ingenting")

    val html = KVITTERING
        .replace("{{json}}", json.toPrettyString())
        .replace("{{metadata}}", metadata.toPrettyString())
        .replace("{{kibana}}", "https://logs.adeo.no/app/kibana#/discover?_a=(index:'tjenestekall-*',query:(language:lucene,query:'$query'))&_g=(time:(from:'${from}',mode:absolute,to:now))")
        .replace("{{consoleCloudGoogle}}", "https://console.cloud.google.com/logs/query;query=jsonPayload.message:${query};customDuration=today?project=${projectId}")
        .velgTema(MonthDay.now())

    call.respondText(html, ContentType.Text.Html)
}

private fun sendÉnMelding(sender: Sender, avsender: Avsender, begrunnelse: String, melding: ObjectNode): SendtMelding {
    return try {
        val tidspunkt = LocalDateTime.now()

        val json = objectMapper.readTree(
            Template.resolve(
                input = melding.toString(),
                navIdent = avsender.navIdent,
                navn = avsender.navn,
                epost = avsender.epost,
                tidspunkt = tidspunkt,
                fødselsnummer = melding.fødselsnummerOrNull ?: "n/a",
                begrunnelse = begrunnelse
            )
        ) as ObjectNode

        val id = json.alfakrøllId
        val eventName = json.eventName

        val fødselsnummer = json.fødselsnummerOrNull?.also {
            check(it.matches("\\d{11}".toRegex())) { "Gyldig 'fødselsnummer' må settes i meldingen" }
        }
        check(eventName.isNotBlank()) { "Må settes '@event_name' i meldingen" }
        json.replace("@avsender", avsender.json)

        val (metadata, sendtMelding) = sender.send(fødselsnummer, json, tidspunkt, id)
        AuditOgSikkerlogg.logg(
            message = "Sendt melding fra Spout\nMelding:\n\t$sendtMelding\nMetadata:\n\t$metadata",
            navIdent = avsender.navIdent,
            fødselsnummer = fødselsnummer,
            tidspunkt = tidspunkt,
            eventName = eventName,
            id = id,
            begrunnelse = begrunnelse
        )
        return SendtMelding(metadata, sendtMelding, id, tidspunkt)
    } catch (ex: Exception) {
        logger.error("Feil ved sending av melding", ex)
        ex.somSendtMelding
    }
}

private data class SendtMelding(val metadata: ObjectNode, val melding: ObjectNode, val id: UUID, val tidspunkt: LocalDateTime) {
    companion object {
        private val epoch = LocalDate.EPOCH.atStartOfDay()
        private val nullId = UUID.fromString("00000000-0000-0000-0000-00000000000")
        val Exception.somSendtMelding get() = SendtMelding(
            metadata = objectMapper.createObjectNode().put("feil", "${this.message}"),
            melding =  objectMapper.createObjectNode(),
            tidspunkt = epoch,
            id = nullId
        )
        fun List<SendtMelding>.kvittering(selector: (sendtMelding: SendtMelding) -> ObjectNode): JsonNode {
            if (size == 1) return selector(first())
            return map { selector(it) }.let { meldinger ->
                objectMapper.createArrayNode().apply { addAll(meldinger) }
            }
        }
    }
}

private data class Avsender(val navIdent: String, val navn: String, val epost: String, val begrunnelse: String) {
    val json = objectMapper.createObjectNode()
        .put("NAVIdent", navIdent)
        .put("navn", navn)
        .put("epost", epost)
        .put("begrunnelse", begrunnelse)
}

private data class SpoutRequest(val avsender: Avsender, val begrunnelse: String, private val json: ObjectNode) : Iterable<ObjectNode> {
    override operator fun iterator() = object : Iterator<ObjectNode> {
        private var jsonArray = when (json.has("bulk")) {
            true -> (json.path("bulk") as ArrayNode).map { it as ObjectNode }
            else -> listOf(json)
        }

        override fun hasNext() = jsonArray.isNotEmpty()

        override fun next(): ObjectNode {
            check(hasNext()) { "Har ingen fler entires" }
            val neste = jsonArray.first()
            jsonArray = jsonArray.drop(1)
            return neste
        }
    }
}

private val ObjectNode.eventName get() = path("@event_name").asText()

private val DissaMåDuSetteAlfakrøllIdPå = setOf(
    "inntektsmelding",
    "arbeidsgiveropplysninger",
    "korrigerte_arbeidsgiveropplysninger",
    "selvbestemte_korrigerte_arbeidsgiveropplysninger",
    "sendt_søknad",
    "ny_søknad"
)

private val ObjectNode.alfakrøllId get() = eventName.let { event -> when {
    DissaMåDuSetteAlfakrøllIdPå.any { event.startsWith(it) } -> try { UUID.fromString(path("@id").asText()) } catch (_: Exception) {
        error("Du må sette den opprinnelige @id når de spouter $event, ellers blir det krøll i Spedisjon med mapping mellom intern og ekstern id!!")
    }
    else -> UUID.randomUUID()
}}
