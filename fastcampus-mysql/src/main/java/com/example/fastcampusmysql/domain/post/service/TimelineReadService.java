package com.example.fastcampusmysql.domain.post.service;

import com.example.fastcampusmysql.domain.post.entity.Timeline;
import com.example.fastcampusmysql.domain.post.repository.TimelineRepository;
import com.example.fastcampusmysql.util.CusorRequest;
import com.example.fastcampusmysql.util.PageCursor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class TimelineReadService {

    private final TimelineRepository timelineRepository;

    public TimelineReadService(TimelineRepository timelineRepository) {
        this.timelineRepository = timelineRepository;
    }

    public PageCursor<Timeline> getTimelines(Long memberId, CusorRequest request) {
        var timelines =  findAllBy(memberId, request);
        var nextKey = timelines.stream()
                .mapToLong(Timeline::getId)
                .min().orElse(CusorRequest.NONE_KEY);

        return new PageCursor<>(request.next(nextKey), timelines);
    }

    private List<Timeline> findAllBy(Long memberId, CusorRequest request) {
        if (request.hasKey()) {
            return timelineRepository.findAllByLessThanMemberIdAndOrderByIdDesc(request.key(), memberId, request.size());
        }

        return timelineRepository.findAllByMemberIdOrderByIdDesc(memberId, request.size());
    }
}
