package com.example.service;

import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.JoinWindows;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Printed;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
public class StreamService {

    private static final Serde<String> STRING_SERDE = Serdes.String();

    @Autowired
    public void buildPipeline(StreamsBuilder sb) {
//        KStream<String, String> stream = sb.stream("fastcompus", Consumed.with(STRING_SERDE, STRING_SERDE));
//        stream.filter((key, value) -> value.contains("class")).to("classList");

        KStream<String, String> leftStream = sb.stream("leftTopic", Consumed.with(STRING_SERDE, STRING_SERDE)).selectKey((k, v) -> v.substring(0, v.indexOf(":")));
        KStream<String, String> rightStream = sb.stream("leftTopic", Consumed.with(STRING_SERDE, STRING_SERDE)).selectKey((k, v) -> v.substring(0, v.indexOf(":")));

        leftStream.print(Printed.toSysOut());
        rightStream.print(Printed.toSysOut());

        KStream<String, String> joinedStream = leftStream.join(rightStream,
                (leftValue, rightValue) -> leftValue + "_" + rightValue,
                JoinWindows.ofTimeDifferenceWithNoGrace(Duration.ofMinutes(1)));

        joinedStream.print(Printed.toSysOut());

        joinedStream.to("joinedMsg");
    }
}
