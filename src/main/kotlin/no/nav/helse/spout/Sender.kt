package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.producer.RecordMetadata
import java.time.LocalDateTime
import java.util.*

internal abstract class Sender(
    protected val instance: String,
    protected val image: String) {

    protected abstract fun send(fødselsnummer: String?, melding: String): RecordMetadata

    internal fun send(key: String?, melding: ObjectNode, tidspunkt: LocalDateTime, id: UUID): Pair<ObjectNode, ObjectNode> {
        melding.replace("system_participating_services", systemParticipatingServices(id, tidspunkt))
        melding.put("@id", "$id")
        melding.put("@opprettet", "$tidspunkt")
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

    private fun systemParticipatingServices(id: UUID, tidspunkt: LocalDateTime) = objectMapper.createArrayNode()
        .add(objectMapper.createObjectNode()
            .put("image", image)
            .put("service", "spout")
            .put("id", "$id")
            .put("time", "$tidspunkt")
            .put("instance", instance)
        )

    private companion object {
        private val objectMapper = jacksonObjectMapper()
    }
}