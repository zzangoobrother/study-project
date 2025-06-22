package com.example.repository;

import com.example.dto.projection.ChannelTitleProjection;
import com.example.dto.projection.InviteCodeProjection;
import com.example.entity.ChannelEntity;
import jakarta.annotation.Nonnull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChannelRepository extends JpaRepository<ChannelEntity, Long> {

    Optional<ChannelTitleProjection> findChannelTitleByChannelId(@Nonnull Long channelId);

    Optional<InviteCodeProjection> findChannelInviteCodeByChannelId(@Nonnull Long channelId);
}
