package no.nav.helse.spout

import io.ktor.server.cio.*
import io.ktor.server.engine.*
import io.ktor.server.routing.*

fun main() {
    embeddedServer(CIO, port = 8080) {
        routing {
            spout(
                sender = TestSender,
                navIdent = { "localhost ident" },
                navn = { "localhost navn" },
                epost =  { "localhost@localhost.auu" }
            )
        }
    }.start(wait = true)
}