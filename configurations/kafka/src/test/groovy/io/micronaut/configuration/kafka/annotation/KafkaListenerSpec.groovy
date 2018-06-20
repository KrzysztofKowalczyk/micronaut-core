package io.micronaut.configuration.kafka.annotation

import groovy.transform.EqualsAndHashCode
import io.micronaut.configuration.kafka.config.AbstractKafkaConfiguration
import io.micronaut.configuration.kafka.config.AbstractKafkaProducerConfiguration
import io.micronaut.configuration.kafka.serde.JsonSerde
import io.micronaut.context.ApplicationContext
import io.micronaut.messaging.MessageHeaders
import io.micronaut.messaging.annotation.Header
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.header.internals.RecordHeader
import org.apache.kafka.common.serialization.StringSerializer
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

class KafkaListenerSpec extends Specification {


    void "test simple consumer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(
                new ProducerRecord(
                        "words",
                        null,
                        "key",
                        "hello world",
                        Collections.singletonList(
                                new RecordHeader("topic", "words".bytes)
                        )
                )
        ).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer myConsumer = context.getBean(MyConsumer)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.lastTopic == 'words'
        }

        cleanup:
        producer.close()
        context.close()

    }


    void "test POJO consumer"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        config.setKeySerializer(new StringSerializer())
        config.setValueSerializer(new JsonSerde(Book).serializer())
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("books", "Stephen King", new Book(title: "The Stand"))).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        PojoConsumer myConsumer = context.getBean(PojoConsumer)
        then:
        conditions.eventually {
            myConsumer.lastBook == new Book(title: "The Stand")
            myConsumer.messageHeaders != null
        }

        cleanup:
        producer.close()
        context.close()

    }


    void "test @KafkaKey annotation"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer2 myConsumer = context.getBean(MyConsumer2)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.key == "key"
        }

        cleanup:
        producer.close()
        context.close()

    }

    void "test receive ConsumerRecord"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                Collections.singletonMap(
                        AbstractKafkaConfiguration.EMBEDDED, true
                )

        )

        when:
        def config = context.getBean(AbstractKafkaProducerConfiguration)
        KafkaProducer producer = context.createBean(KafkaProducer, config)
        producer.send(new ProducerRecord("words-records", "key", "hello world")).get()

        PollingConditions conditions = new PollingConditions(timeout: 5, delay: 1)

        MyConsumer3 myConsumer = context.getBean(MyConsumer3)
        then:
        conditions.eventually {
            myConsumer.wordCount == 2
            myConsumer.key == "key"
        }

        cleanup:
        producer.close()
        context.close()

    }


    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer {
        int wordCount
        String lastTopic

        @Topic("words")
        void countWord(String sentence, @Header String topic) {
            wordCount += sentence.split(/\s/).size()
            lastTopic = topic
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer2 {
        int wordCount
        String key

        @Topic("words")
        void countWord(@KafkaKey String key, String sentence) {
            wordCount += sentence.split(/\s/).size()
            this.key = key
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class MyConsumer3 {
        int wordCount
        String key

        @Topic("words-records")
        void countWord(@KafkaKey String key, ConsumerRecord<String, String> record) {
            wordCount += record.value().split(/\s/).size()
            this.key = key
        }
    }

    @KafkaListener(offsetReset = OffsetReset.EARLIEST)
    static class PojoConsumer {
        Book lastBook
        MessageHeaders messageHeaders

        @Topic("books")
        void receiveBook(Book book, MessageHeaders messageHeaders) {
            lastBook = book
            this.messageHeaders = messageHeaders

        }
    }

    @EqualsAndHashCode
    static class Book {
        String title
    }
}
