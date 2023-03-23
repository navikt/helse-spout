package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.LocalDateTime
import java.util.*

internal abstract class Sender(
    protected val instance: String,
    protected val image: String) {

    protected abstract fun send(fødselsnummer: String, melding: String): RecordMetadata

    internal fun send(key: String, melding: ObjectNode): Pair<ObjectNode, ObjectNode> {
        val id = UUID.randomUUID()
        val opprettet = LocalDateTime.now()
        melding.replace("system_participating_services", systemParticipatingServices(id, opprettet))
        melding.put("@id", "$id")
        melding.put("@opprettet", "$opprettet")
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

    private fun systemParticipatingServices(id: UUID, opprettet: LocalDateTime) = objectMapper.createArrayNode()
        .add(objectMapper.createObjectNode()
            .put("image", image)
            .put("service", "spout")
            .put("id", "$id")
            .put("time", "$opprettet")
            .put("instance", instance)
        )

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}