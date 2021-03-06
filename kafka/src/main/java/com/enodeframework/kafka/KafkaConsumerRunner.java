package com.enodeframework.kafka;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.errors.WakeupException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

public class KafkaConsumerRunner<K, V> implements Runnable {
    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerRunner.class);
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private KafkaConsumer<K, V> kafkaConsumer;

    private IMessageListener messageListener;

    KafkaConsumerRunner(KafkaConsumer<K, V> kafkaConsumer, IMessageListener messageListener) {
        this.kafkaConsumer = kafkaConsumer;
        this.messageListener = messageListener;
    }

    @Override
    public void run() {
        try {
            while (!closed.get()) {
                try {
                    ConsumerRecords<K, V> records = kafkaConsumer.poll(Duration.ofMillis(1000));
                    for (ConsumerRecord record : records) {
                        messageListener.receiveMessage(record, message -> {
                            logger.info("run something:{}", message.getBody());
                            return;
                        });
                    }
                } catch (Exception e) {
                    logger.error("consumer message failed", e);
                }
            }
        } catch (WakeupException e) {
            // Ignore exception if closing
            if (!closed.get()) {
                throw e;
            }
        } finally {
            kafkaConsumer.close();
        }
    }

    public void shutdown() {
        closed.set(true);
        kafkaConsumer.wakeup();
    }

}
