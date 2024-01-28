package com.example.kafkaProducerPractice.service;

import com.example.kafkaProducerPractice.vo.EffectOrNot;
import com.example.kafkaProducerPractice.vo.PurchaseLog;
import com.example.kafkaProducerPractice.vo.PurchaseLogOneProduct;
import com.example.kafkaProducerPractice.vo.WatchingAdLog;
import lombok.Getter;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.*;
import org.apache.kafka.streams.state.KeyValueStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Getter
public class AdEvaluationService {
    // 광고 데이터 중복 join 될 필요없다. --> Table
    // 광고 이력이 먼저 들어옴
    // 구매 이력은 상품별로 들어오지 않는다. 복수개의 상품 존재
    // 광고에 머문시간이 10초 이상되어야만 join 대상
    // 특정가격 이상의 상품은 join 대상에서 제외
    // 광고이력 : KTable(AdLog), 구매이력 : KTable(PurchaseLogOneProduct)
    // EffectOrNot --> Json 형태로 Topic : AdEvaluationComplete

    private final Producer producer;

    public AdEvaluationService(Producer producer) {
        this.producer = producer;
    }

    @Autowired
    public void builedPipeline(StreamsBuilder sb) {
        JsonSerializer<EffectOrNot> effectSerializer = new JsonSerializer<>();
        JsonSerializer<PurchaseLog> purchaseLogSerializer = new JsonSerializer<>();
        JsonSerializer<WatchingAdLog> watchingAdLogSerializer = new JsonSerializer<>();
        JsonSerializer<PurchaseLogOneProduct> purchaseLogOneSerializer = new JsonSerializer<>();

        JsonDeserializer<EffectOrNot> effectDeserializer = new JsonDeserializer<>(EffectOrNot.class);
        JsonDeserializer<PurchaseLog> purchaseLogDeserializer = new JsonDeserializer<>(PurchaseLog.class);
        JsonDeserializer<WatchingAdLog> watchingAdLogDeserializer = new JsonDeserializer<>(WatchingAdLog.class);
        JsonDeserializer<PurchaseLogOneProduct> purchaseLogOneDeserializer = new JsonDeserializer<>(PurchaseLogOneProduct.class);

        Serde<EffectOrNot> effectOrNotSerde = Serdes.serdeFrom(effectSerializer, effectDeserializer);
        Serde<PurchaseLog> purchaseLogSerde = Serdes.serdeFrom(purchaseLogSerializer, purchaseLogDeserializer);
        Serde<WatchingAdLog> watchingLogSerde = Serdes.serdeFrom(watchingAdLogSerializer, watchingAdLogDeserializer);
        Serde<PurchaseLogOneProduct> purchaseLogOneSerde = Serdes.serdeFrom(purchaseLogOneSerializer, purchaseLogOneDeserializer);

        KTable<String, WatchingAdLog> adTable = sb.stream("AdLog", Consumed.with(Serdes.String(), watchingLogSerde))
                .selectKey((k, v) -> v.getUserId() + "_" + v.getProductId())
                .toTable(Materialized.<String, WatchingAdLog, KeyValueStore<Bytes, byte[]>>as("adStore")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(watchingLogSerde));

        KStream<String, PurchaseLog> purchaseLogKStream = sb.stream("PurchaseLog", Consumed.with(Serdes.String(), purchaseLogSerde));

        purchaseLogKStream.foreach((k, v) -> {
            for (Map<String, String> prodInfo : v.getProductInfo()) {
                if (Integer.parseInt(prodInfo.get("price")) < 1000000) {
                    PurchaseLogOneProduct myTempVo = new PurchaseLogOneProduct();
                    myTempVo.setUserId(v.getUserId());
                    myTempVo.setProductId(prodInfo.get("productId"));
                    myTempVo.setOrderId(v.getOrderId());
                    myTempVo.setPrice(prodInfo.get("price"));
                    myTempVo.setPurchasedDt(v.getPurchasedDt());

                    producer.sendJoinedMessage("oneProduct", myTempVo);
                }
            }
        });

        KTable<String, PurchaseLogOneProduct> purchaseLogOneKTable = sb.stream("PurchaseLogOneProduct", Consumed.with(Serdes.String(), purchaseLogOneSerde))
                .selectKey((k, v) -> v.getUserId() + "_" + v.getProductId())
                .toTable(Materialized.<String, PurchaseLogOneProduct, KeyValueStore<Bytes, byte[]>>as("purchaseLogStore")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(purchaseLogOneSerde));

        ValueJoiner<WatchingAdLog, PurchaseLogOneProduct, EffectOrNot> tableStreamJoiner = (leftValue, rightValue) -> {
            EffectOrNot returnValue = new EffectOrNot();
            returnValue.setUserId(leftValue.getUserId());
            returnValue.setAdId(leftValue.getAdId());
            Map<String, String> tempProductInfo = new HashMap<>();
            tempProductInfo.put("productId", rightValue.getProductId());
            tempProductInfo.put("price", rightValue.getPrice());
            returnValue.setProductInfo(tempProductInfo);
            returnValue.setOrderId(rightValue.getOrderId());

            return returnValue;
        };

        adTable.join(purchaseLogOneKTable, tableStreamJoiner).toStream().to("AdEvaluationComplete", Produced.with(Serdes.String(), effectOrNotSerde));
    }
}
