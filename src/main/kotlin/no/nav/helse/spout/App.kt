package no.nav.helse.spout

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.slf4j.LoggerFactory

private val sikkerlogg = LoggerFactory.getLogger("tjenestekall")

fun main() {
    embeddedServer(CIO, port = 8080) {
        routing {
            get("/isalive") { call.respondText("ALIVE!") }
            get("/isready") { call.respondText("READY!") }
            post("/melding") {
                sikkerlogg.info("Sender melding")
                call.respond(HttpStatusCode.Accepted)
            }
        }
    }.start(wait = true)
}