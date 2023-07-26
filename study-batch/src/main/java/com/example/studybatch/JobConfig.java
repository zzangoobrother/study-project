package com.example.studybatch;

import com.example.studybatch.domain.Member;
import com.example.studybatch.domain.MemberRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.configuration.annotation.EnableBatchProcessing;
import org.springframework.batch.core.configuration.annotation.JobBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepBuilderFactory;
import org.springframework.batch.core.configuration.annotation.StepScope;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.batch.item.ItemWriter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
@EnableBatchProcessing
@RequiredArgsConstructor
public class JobConfig {

    private final JobBuilderFactory jobBuilderFactory;
    private final StepBuilderFactory stepBuilderFactory;
    private final MemberRepository memberRepository;

    @Bean
    public Job exampleJob() {
        return jobBuilderFactory.get("exampleJob")
//                .preventRestart()
                .start(start())
                .build();
    }

    @Bean
    public Step start() {
        return stepBuilderFactory.get("step")
                .<Member, Member>chunk(10)
                .reader(reader())
                .processor(processor())
                .writer(writer())
                .build();
    }

    @Bean
    @StepScope
    public QueueItemReader<Member> reader() {
        List<Member> result = memberRepository.findAllByPrice();
        return new QueueItemReader<>(result);
    }

    @Bean
    @StepScope
    public ItemProcessor<Member, Member> processor() {
        return new ItemProcessor<Member, Member>() {
            @Override
            public Member process(Member member) throws Exception {
                member.minusPrice();
                return member;
            }
        };
    }

    @Bean
    @StepScope
    public ItemWriter<Member> writer() {
        return ((List<? extends Member> members) -> memberRepository.saveAll(members));
    }
}
