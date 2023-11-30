package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.intellij.lang.annotations.Language
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID

internal class TemplateTest {

    @Test
    fun `resolver ut uuider`() {
        val input = """{ "uuid": "{{uuidgen}}"}"""
        val result = resolve(input)
        assertDoesNotThrow { UUID.fromString(result.path("uuid").asText()) }
    }

    @Test
    fun `resolver tidspunkt`() {
        @Language("JSON")
        val input = """{ 
            "tidspunkt": "{{now}}",
            "tidspunktPlus1h": "{{now+1h}}",
            "tidspunktPlus2d": "{{now+2d}}",
            "tidspunktMinus3h": "{{now-3h}}",
            "tidspunktMinus4d": "{{now-4d}}"
        }"""
        val result = resolve(input)
        assertEquals(tidspunkt, LocalDateTime.parse(result.path("tidspunkt").asText()))
        assertEquals(tidspunkt.plusHours(1), LocalDateTime.parse(result.path("tidspunktPlus1h").asText()))
        assertEquals(tidspunkt.plusDays(2), LocalDateTime.parse(result.path("tidspunktPlus2d").asText()))
        assertEquals(tidspunkt.minusHours(3), LocalDateTime.parse(result.path("tidspunktMinus3h").asText()))
        assertEquals(tidspunkt.minusDays(4), LocalDateTime.parse(result.path("tidspunktMinus4d").asText()))
    }

    @Test
    fun `resolver datoer`() {
        @Language("JSON")
        val input = """{ 
            "dato": "{{today}}",
            "datoPlus1d": "{{today+1d}}",
            "datoMinus1d": "{{today-1d}}"
        }"""
        val result = resolve(input)
        assertEquals(tidspunkt.toLocalDate(), LocalDate.parse(result.path("dato").asText()))
        assertEquals(tidspunkt.toLocalDate().plusDays(1), LocalDate.parse(result.path("datoPlus1d").asText()))
        assertEquals(tidspunkt.toLocalDate().minusDays(1), LocalDate.parse(result.path("datoMinus1d").asText()))
    }

    @Test
    fun `resolver info om innlogget brukes`() {
        @Language("JSON")
        val input = """{ 
            "navIdent": "{{NAVIdent}}",
            "navn": "{{navn}}",
            "epost": "{{epost}}"
        }"""
        val result = resolve(input)
        assertEquals("NAV Ident", result.path("navIdent").asText())
        assertEquals("Navn Navnesen", result.path("navn").asText())
        assertEquals("navn@epost.no", result.path("epost").asText())
    }
    @Test
    fun `resolver fødselsnummer`() {
        @Language("JSON")
        val input = """{ 
            "fødselsnummer": "{{fødselsnummer}}"
        }"""
        val result = resolve(input)
        assertEquals("12345678910", result.path("fødselsnummer").asText())
    }

    private val tidspunkt = LocalDateTime.parse("2023-03-23T23:00:00.000000")
    private val objectMapper = jacksonObjectMapper()
    private fun resolve(input: String) = Template.resolve(
        input = input,
        navIdent = "NAV Ident",
        navn = "Navn Navnesen",
        epost = "navn@epost.no",
        tidspunkt = tidspunkt,
        fødselsnummer = "12345678910",
        begrunnelse = "en lang begrunnelse her"
    ).let { objectMapper.readTree(it) as ObjectNode }
}
