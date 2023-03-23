package no.nav.helse.spout

import com.auth0.jwk.JwkProviderBuilder
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.net.URL

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
internal val String.env get() = checkNotNull(System.getenv(this)) { "Fant ikke environment variable $this" }
private val ApplicationCall.NAVident get() =  principal<JWTPrincipal>()!!["NAVident"] ?: throw IllegalStateException("Fant ikke NAVident")
private val ApplicationCall.navn get() =  principal<JWTPrincipal>()!!["name"] ?: throw IllegalStateException("Fant ikke NAVident")
private val ApplicationCall.epost get() =  principal<JWTPrincipal>()!!["preferred_username"] ?: throw IllegalStateException("Fant ikke NAVident")

private val SEND = object {}.javaClass.getResource("/send.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke index.html")
private val KVITTERING = object {}.javaClass.getResource("/kvittering.html")?.readText(Charsets.UTF_8) ?: throw IllegalStateException("Fant ikke index.html")
private fun Parameters.hent(key: String) = checkNotNull(get(key)?.takeUnless { it.isBlank() }) { "Mangler $key" }
private val objectMapper = jacksonObjectMapper()

fun main() {
    embeddedServer(CIO, port = 8080) {
        authentication {
            jwt {
                val jwkProvider = JwkProviderBuilder(URL("AZURE_OPENID_CONFIG_JWKS_URI".env)).build()
                verifier(jwkProvider, "AZURE_OPENID_CONFIG_ISSUER".env) {
                    withAudience("AZURE_APP_CLIENT_ID".env)
                    withArrayClaim("groups", "TBD_GROUP_ID".env)
                    withClaimPresence("NAVident")
                    withClaimPresence("preferred_username")
                    withClaimPresence("name")
                }
                validate { credentials -> JWTPrincipal(credentials.payload) }
            }
        }

        routing {
            get("/isalive") { call.respondText("ALIVE!") }
            get("/isready") { call.respondText("READY!") }
            authenticate {
                get {
                    call.respondText(SEND, ContentType.Text.Html)
                }
                post("/melding") {
                    val avsender = jacksonObjectMapper().createObjectNode()
                        .put("NAVIdent", call.NAVident)
                        .put("navn", call.navn)
                        .put("epost", call.epost)

                    val parameters = call.receiveParameters()
                    val fødselsnummer = parameters.hent("fodselsnummer")

                    val json = objectMapper.readTree(parameters.hent("json")) as ObjectNode
                    json.put("@event_name", parameters.hent("event_name"))
                    json.put("fødselsnummer", fødselsnummer)
                    json.put("aktørId", parameters.hent("aktorId"))
                    json.replace("@avsender", avsender)

                    val (metadata, melding) = Kafka.send(fødselsnummer, json)
                    sikkerlogg.info("Sendt melding fra Spout\nMelding:\t: $melding\nMetadata:\t $metadata")

                    val html = KVITTERING
                        .replace("{{json}}", melding.toPrettyString())
                        .replace("{{metadata}}", metadata.toPrettyString())

                    call.respondText(html, ContentType.Text.Html)
                }
            }
        }
    }.start(wait = true)
}