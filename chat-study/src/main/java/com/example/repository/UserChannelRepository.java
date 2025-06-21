package com.example.repository;

import com.example.entity.UserChannelId;
import com.example.entity.UserChannelEntity;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserChannelRepository extends JpaRepository<UserChannelEntity, UserChannelId> {

    boolean existsByUserIdAndChannelId(@Nonnull Long userId, @Nonnull Long channelId);
}
