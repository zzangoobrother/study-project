package com.example.handler.kafka;

import com.example.dto.kafka.outbound.InviteResponseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class InviteResponseRecordHandler implements BaseRecordHandler<InviteResponseRecord> {

    private static final Logger log = LoggerFactory.getLogger(InviteResponseRecordHandler.class);

    @Override
    public void handleRecord(InviteResponseRecord record) {
        log.info("{} to offline userId : {}", record, record.userId());
    }
}
