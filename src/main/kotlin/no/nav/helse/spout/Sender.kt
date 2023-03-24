package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import no.nav.helse.rapids_rivers.JsonMessage
import org.apache.kafka.clients.producer.RecordMetadata

internal abstract class Sender() {

    protected abstract fun send(fødselsnummer: String, melding: String): RecordMetadata

    internal fun send(key: String, melding: JsonMessage): Pair<ObjectNode, JsonMessage> {
        val metadata = send(key, melding.toString()).let { metadata ->
            objectMapper.createObjectNode()
                .put("topic", metadata.topic())
                .put("offset", metadata.offset())
                .put("partition", metadata.partition())
                .put("timestamp", metadata.timestamp())
                .put("serializedKeySize", metadata.serializedKeySize())
                .put("serializedValueSize", metadata.serializedValueSize())
        }
        return metadata to melding
    }

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}