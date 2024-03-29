package com.example.kafkaProducerPractice.config;

import com.example.kafkaProducerPractice.util.CustomSerializer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.StreamsConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.annotation.EnableKafkaStreams;
import org.springframework.kafka.annotation.KafkaStreamsDefaultConfiguration;
import org.springframework.kafka.config.KafkaStreamsConfiguration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@EnableKafkaStreams
@EnableKafka
@Configuration
public class KafkaConfig {

    @Bean(name = KafkaStreamsDefaultConfiguration.DEFAULT_STREAMS_CONFIG_BEAN_NAME)
    public KafkaStreamsConfiguration myKafkaStreamConfig() {
        Map<String, Object> myConfig = new HashMap<>();
        myConfig.put(StreamsConfig.APPLICATION_ID_CONFIG, "stream-test");
        myConfig.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "3.35.52.224:9092");
        myConfig.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        myConfig.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.String().getClass().getName());
        myConfig.put(StreamsConfig.NUM_STREAM_THREADS_CONFIG, 3);
        myConfig.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all");
        myConfig.put(StreamsConfig.topicPrefix(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG), 2);
        myConfig.put(StreamsConfig.NUM_STANDBY_REPLICAS_CONFIG, 1);

        return new KafkaStreamsConfiguration(myConfig);
    }


    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> myConfig = new HashMap<>();
        myConfig.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "3.35.52.224:9092");
        myConfig.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        myConfig.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, CustomSerializer.class);

        return new DefaultKafkaProducerFactory<>(myConfig);
    }
//
//    @Bean
//    public ConsumerFactory<String, Object> consumerFactory() {
//        Map<String, Object> myConfig = new HashMap<>();
//        myConfig.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "3.35.52.224:9092");
//        myConfig.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//        myConfig.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
//
//        return new DefaultKafkaConsumerFactory<>(myConfig);
//    }
//
//    @Bean
//    public ConcurrentKafkaListenerContainerFactory<String, Object> kafkaListenerContainerFactory() {
//        ConcurrentKafkaListenerContainerFactory<String, Object> factory = new ConcurrentKafkaListenerContainerFactory<>();
//        factory.setConsumerFactory(consumerFactory());
//        return factory;
//    }
}
