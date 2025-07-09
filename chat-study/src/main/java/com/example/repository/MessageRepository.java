package com.example.repository;

import com.example.dto.projection.MessageInfoProjection;
import com.example.entity.MessageEntity;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MessageRepository extends JpaRepository<MessageEntity, Long> {

    @Query("select max(m.messageSequence) from MessageEntity m where m.channelId = :channelId")
    Optional<Long> findLastMessageSequenceByChannelId(@Param("channelId") Long channelId);

    List<MessageInfoProjection> findByChannelIdAndMessageSequenceBetween(@Nonnull Long channelId, @Nonnull Long startMessageSequence, @Nonnull Long endMessageSequence);
}
