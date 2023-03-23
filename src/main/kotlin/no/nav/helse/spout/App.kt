package no.nav.helse.spout

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory
import java.net.URL

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")
private val String.env get() = checkNotNull(System.getenv(this)) { "Fant ikke environment variable $this" }

fun main() {
    embeddedServer(CIO, port = 8080) {
        authentication {
            jwt {
                val jwkProvider = JwkProviderBuilder(URL("AZURE_OPENID_CONFIG_JWKS_URI".env)).build()
                verifier(jwkProvider, "AZURE_OPENID_CONFIG_ISSUER".env) {
                    withAudience("AZURE_APP_CLIENT_ID".env)
                    withClaimPresence("NAVident")
                }
                validate { credentials -> JWTPrincipal(credentials.payload) }
            }
        }

        routing {
            get("/isalive") { call.respondText("ALIVE!") }
            get("/isready") { call.respondText("READY!") }
            authenticate {
                get("/melding") {
                    val navIdent = call.principal<JWTPrincipal>()!!["NAVident"] ?: throw IllegalStateException("Fant ikke NAVident")
                    sikkerlogg.info("$navIdent Sender melding")
                    call.respond(HttpStatusCode.Accepted)
                }
            }
        }
    }.start(wait = true)
}