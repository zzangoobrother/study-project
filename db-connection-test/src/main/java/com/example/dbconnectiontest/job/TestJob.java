package com.example.dbconnectiontest.job;

import com.example.dbconnectiontest.model.Member;
import com.example.dbconnectiontest.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
@Component
public class TestJob {

    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher publisher;

    @Scheduled(fixedDelay = 10000)
    @Transactional
    public void testSchedule() {
        List<Member> members = memberRepository.findAll();

        members.forEach(m -> {
            publisher.publishEvent(m);
        });
    }
}
