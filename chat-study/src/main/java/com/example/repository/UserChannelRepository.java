package com.example.repository;

import com.example.dto.domain.ChannelId;
import com.example.dto.projection.UserIdProjection;
import com.example.entity.UserChannelEntity;
import com.example.entity.UserChannelId;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserChannelRepository extends JpaRepository<UserChannelEntity, UserChannelId> {

    boolean existsByUserIdAndChannelId(@Nonnull Long userId, @Nonnull Long channelId);

    List<UserIdProjection> findUserIdByChannelId(@Nonnull Long channelId);
}
