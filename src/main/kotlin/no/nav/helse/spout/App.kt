package no.nav.helse.spout

import com.auth0.jwk.JwkProviderBuilder
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.net.URL

internal val String.env get() = checkNotNull(System.getenv(this)) { "Fant ikke environment variable $this" }
internal fun String.env(default: String) = System.getenv(this)?: default
private val ApplicationCall.NAVident get() =  principal<JWTPrincipal>()!!["NAVident"] ?: throw IllegalStateException("Fant ikke NAVident")
private val ApplicationCall.navn get() =  principal<JWTPrincipal>()!!["name"] ?: throw IllegalStateException("Fant ikke NAVident")
private val ApplicationCall.epost get() =  principal<JWTPrincipal>()!!["preferred_username"] ?: throw IllegalStateException("Fant ikke NAVident")

fun main() {
    embeddedServer(CIO, port = 8080, module = Application::spout).start(wait = true)
}

private fun Application.spout() {
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
            spout(
                sender = Kafka,
                resolveNavIdent = { it.NAVident },
                resolveNavn = { it.navn },
                resolveEpost = { it.epost }
            )
        }
    }
}