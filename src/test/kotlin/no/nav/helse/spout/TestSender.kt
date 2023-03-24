package no.nav.helse.spout

import org.apache.kafka.clients.producer.RecordMetadata
import org.apache.kafka.common.TopicPartition

internal object TestSender: Sender() {
    override fun send(fødselsnummer: String, melding: String) = RecordMetadata(
        TopicPartition("tbd.localhost.v1", 1), 2L, 3,4,5,6)
}