package no.nav.helse.spout

import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.apache.kafka.clients.CommonClientConfigs
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.config.SslConfigs
import org.apache.kafka.common.security.auth.SecurityProtocol
import org.apache.kafka.common.serialization.StringSerializer
import java.net.InetAddress
import java.time.LocalDateTime
import java.util.Properties
import java.util.UUID

internal object Kafka {
    private const val TOPIC = "tbd.rapid.v1"
    private val clientId = InetAddress.getLocalHost().hostName
    private val objectMapper = jacksonObjectMapper()

    private val properties = Properties().apply {
        put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, "KAFKA_BROKERS".env)
        put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, SecurityProtocol.SSL.name)
        put(SslConfigs.SSL_ENDPOINT_IDENTIFICATION_ALGORITHM_CONFIG, "")
        put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "jks")
        put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PKCS12")
        put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, "KAFKA_TRUSTSTORE_PATH".env)
        put(SslConfigs.SSL_TRUSTSTORE_PASSWORD_CONFIG, "KAFKA_CREDSTORE_PASSWORD".env)
        put(SslConfigs.SSL_KEYSTORE_LOCATION_CONFIG, "KAFKA_KEYSTORE_PATH".env)
        put(SslConfigs.SSL_KEYSTORE_PASSWORD_CONFIG, "KAFKA_CREDSTORE_PASSWORD".env)
        put(ProducerConfig.CLIENT_ID_CONFIG, clientId)
        put(ProducerConfig.ACKS_CONFIG, "1")
        put(ProducerConfig.LINGER_MS_CONFIG, "0")
        put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, "1")
    }
    private val producer = KafkaProducer(properties, StringSerializer(), StringSerializer())

    internal fun send(key: String, melding: ObjectNode): Pair<ObjectNode, ObjectNode> {
        val id = UUID.randomUUID()
        val opprettet = LocalDateTime.now()
        melding.replace("system_participating_services", systemParticipatingServices(id, opprettet))
        melding.put("@id", "$id")
        melding.put("@opprettet", "$opprettet")
        val metadata = producer.send(ProducerRecord(TOPIC, key, melding.toString())).get().let { metadata ->
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
            .put("image", "NAIS_APP_IMAGE".env)
            .put("service", "NAIS_APP_NAME".env)
            .put("id", "$id")
            .put("time", "$opprettet")
            .put("instance", clientId)
    )
}