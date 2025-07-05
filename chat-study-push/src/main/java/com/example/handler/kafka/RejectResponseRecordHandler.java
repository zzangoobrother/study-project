package com.example.handler.kafka;

import com.example.dto.kafka.outbound.RejectResponseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class RejectResponseRecordHandler implements BaseRecordHandler<RejectResponseRecord> {

    private static final Logger log = LoggerFactory.getLogger(RejectResponseRecordHandler.class);

    @Override
    public void handleRecord(RejectResponseRecord record) {
        log.info("{} to offline userId : {}", record, record.userId());
    }
}
