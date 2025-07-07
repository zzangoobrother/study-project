package com.example.repository;

import com.example.dto.projection.ChannelProjection;
import com.example.dto.projection.LastReadMsgSeqProjection;
import com.example.dto.projection.UserIdProjection;
import com.example.entity.UserChannelEntity;
import com.example.entity.UserChannelId;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserChannelRepository extends JpaRepository<UserChannelEntity, UserChannelId> {

    boolean existsByUserIdAndChannelId(@Nonnull Long userId, @Nonnull Long channelId);

    List<UserIdProjection> findUserIdByChannelId(@Nonnull Long channelId);

    @Query("select c.channelId, c.title, c.headCount from UserChannelEntity uc inner join ChannelEntity c on uc.channelId = c.channelId where uc.userId = :userId")
    List<ChannelProjection> findChannelsByUserId(@NonNull @Param("userId") Long userId);

    Optional<LastReadMsgSeqProjection> findLastReadMsgSeqByUserIdAndChannelId(@Nonnull Long userId, @Nonnull Long channelId);

    @Modifying
    @Query("UPDATE UserChannelEntity uc set uc.lastReadMsgSeq = :lastReadMsgSeq WHERE uc.userId = :userId and uc.channelId = :channelId")
    int updateLastReadMsgSeqByUserIdAndChannelId(@Param("userId") Long userId, @Param("channelId") Long channelId, @Param("lastReadMsgSeq") Long lastReadMsgSeq);

    void deleteByUserIdAndChannelId(@Nonnull Long userId, @Nonnull Long channelId);
}
