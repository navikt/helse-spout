package no.nav.helse.spout

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import io.ktor.client.request.forms.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.testing.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

internal class MeldingRouteTest {
    private val objectMapper = jacksonObjectMapper()

    private val gyldigSkjema =
        parameters {
            append("begrunnelse", "En helt gyldig begrunnelse")
            append("json", """{"text":"{\"@event_name\":\"test_event\",\"fødselsnummer\":\"12345678910\"}"}""")
        }

    @Test
    fun `svarer med html som før når klienten ikke ber om json`() = spoutTest {
        val response = client.submitForm("/melding", gyldigSkjema)

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
        assertFalse(response.bodyAsText().contains("{{trace}}"))
    }

    @Test
    fun `svarer med json når klienten ber om json`() = spoutTest {
        val response =
            client.submitForm("/melding", gyldigSkjema) {
                accept(ContentType.Application.Json)
            }

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
        val body = objectMapper.readTree(response.bodyAsText())
        val melding = body.path("meldinger").single()
        assertEquals("test_event", melding.path("melding").path("@event_name").asText())
        assertEquals("tbd.localhost.v1", melding.path("metadata").path("topic").asText())
        assertTrue(melding.hasNonNull("id"))
        assertTrue(body.path("lenker").hasNonNull("kibana"))
        assertTrue(body.path("lenker").path("trace").isMissingNode, "Uten aktiv span skal det ikke lages trace-lenke")
    }

    @Test
    fun `svarer med 400 og feil i json når requesten er ugyldig`() = spoutTest {
        val response =
            client.submitForm(
                "/melding",
                parameters {
                    append("begrunnelse", "for kort")
                    append("json", "{}")
                },
            ) {
                accept(ContentType.Application.Json)
            }

        assertEquals(HttpStatusCode.BadRequest, response.status)
        val body = objectMapper.readTree(response.bodyAsText())
        assertTrue(body.path("meldinger").single().path("feil").asText().contains("kort begrunnelse"))
    }

    private fun spoutTest(block: suspend ApplicationTestBuilder.() -> Unit) =
        testApplication {
            routing {
                spout(
                    sender = TestSender,
                    resolveNavIdent = { "A123456" },
                    resolveNavn = { "Test Testesen" },
                    resolveEpost = { "test@nav.no" },
                )
            }
            block()
        }
}
