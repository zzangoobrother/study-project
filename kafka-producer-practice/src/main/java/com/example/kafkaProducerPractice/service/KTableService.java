package com.example.kafkaProducerPractice.service;

import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KTable;
import org.apache.kafka.streams.kstream.ValueJoiner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class KTableService {

    @Autowired
    public void builedPipeline(StreamsBuilder sb) {
        KTable<String, String> leftTable = sb.stream("leftTopic", Consumed.with(Serdes.String(), Serdes.String())).toTable();
        KTable<String, String> rightTable = sb.stream("rightTopic", Consumed.with(Serdes.String(), Serdes.String())).toTable();

        ValueJoiner<String, String, String> stringJoiner = (leftValue, rightValue) -> {
            return "[StringJoiner]" + leftValue + "-" + rightValue;
        };
        KTable<String, String> joinTable = leftTable.join(rightTable, stringJoiner);
        joinTable.toStream().to("joinedMsg");
    }
}
