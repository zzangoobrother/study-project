package com.example.handler.kafka;

import com.example.dto.kafka.outbound.LeaveResponseRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LeaveResponseRecordHandler implements BaseRecordHandler<LeaveResponseRecord> {

    private static final Logger log = LoggerFactory.getLogger(LeaveResponseRecordHandler.class);

    @Override
    public void handleRecord(LeaveResponseRecord record) {
        log.info("{} to offline userId : {}", record, record.userId());
    }
}
