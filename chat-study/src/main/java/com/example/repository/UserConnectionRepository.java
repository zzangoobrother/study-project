package com.example.repository;

import com.example.constants.UserConnectionStatus;
import com.example.dto.projection.InviterUserIdProjection;
import com.example.dto.projection.UserConnectionStatusProjection;
import com.example.entity.UserConnectionEntity;
import com.example.entity.UserConnectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnectionEntity, UserConnectionId> {

    Optional<UserConnectionStatusProjection> findByPartnerAUserIdAndPartnerBUserId(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId);

    Optional<UserConnectionEntity> findByPartnerAUserIdAndPartnerBUserIdAndStatus(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId, @NonNull UserConnectionStatus status);

    Optional<InviterUserIdProjection> findInviterUserIdByPartnerAUserIdAndPartnerBUserId(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId);

}
