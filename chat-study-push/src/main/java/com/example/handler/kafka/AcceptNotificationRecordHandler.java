package com.example.handler.kafka;

import com.example.dto.kafka.outbound.AcceptNotificationRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class AcceptNotificationRecordHandler implements BaseRecordHandler<AcceptNotificationRecord> {

    private static final Logger log = LoggerFactory.getLogger(AcceptNotificationRecordHandler.class);

    @Override
    public void handleRecord(AcceptNotificationRecord record) {
        log.info("{} to offline userId : {}", record, record.userId());
    }
}
