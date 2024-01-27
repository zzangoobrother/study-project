package com.example.kafkaProducerPractice.service;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class StreamService {
    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Autowired
    public void builedPipeline(StreamsBuilder sb) {
//        KStream<String, String> mystream = sb.stream("fastcampus", Consumed.with(STRING_SERDE, STRING_SERDE));
//        mystream.filter((key, value) -> value.contains("freeClass")).to("freeClassList");

        KStream<String, String> leftStream = sb.stream("leftTopic", Consumed.with(STRING_SERDE, STRING_SERDE));
        KStream<String, String> rightStream = sb.stream("rightTopic", Consumed.with(STRING_SERDE, STRING_SERDE));

        ValueJoiner<String, String, String> stringJoiner = (leftValue, rightValue) -> {
            return "[StringJoiner]" + leftValue + "-" + rightValue;
        };

        ValueJoiner<String, String, String> stringOuterJoiner = (leftValue, rightValue) -> {
            return "[stringOuterJoiner]" + leftValue + "<" + rightValue;
        };

        KStream<String, String> joinedStream = leftStream.join(rightStream, stringJoiner, JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)));
        KStream<String, String> outerJoinedStream = leftStream.outerJoin(rightStream, stringOuterJoiner, JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)));

        joinedStream.print(Printed.toSysOut());
        joinedStream.to("joinedMsg");

        outerJoinedStream.print(Printed.toSysOut());
        outerJoinedStream.to("joinedMsg");
    }
}
