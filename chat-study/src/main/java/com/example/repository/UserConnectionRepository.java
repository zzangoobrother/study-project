package com.example.repository;

import com.example.constants.UserConnectionStatus;
import com.example.dto.projection.InviterUserIdProjection;
import com.example.dto.projection.UserConnectionStatusProjection;
import com.example.dto.projection.UserIdUsernameInviterUserIdProjection;
import com.example.entity.UserConnectionEntity;
import com.example.entity.UserConnectionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserConnectionRepository extends JpaRepository<UserConnectionEntity, UserConnectionId> {

    Optional<UserConnectionStatusProjection> findUserConnectionStatusByPartnerAUserIdAndPartnerBUserId(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId);

    Optional<UserConnectionEntity> findByPartnerAUserIdAndPartnerBUserIdAndStatus(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId, @NonNull UserConnectionStatus status);

    Optional<InviterUserIdProjection> findInviterUserIdByPartnerAUserIdAndPartnerBUserId(@NonNull Long partnerAUserId, @NonNull Long partnerBUserId);

    @Query("select u.partnerBUserId as userId, userB.username as username, u.inviterUserId as inviterUserId " +
            "from UserConnectionEntity u " +
            "inner join UserEntity userB " +
            "on u.partnerBUserId = userB.userId " +
            "where u.partnerAUserId = :userId and u.status = :status")
    List<UserIdUsernameInviterUserIdProjection> findByPartnerAUserIdAndStatus(@NonNull @Param("userId") Long userId, @NonNull @Param("status") UserConnectionStatus status);

    @Query("select u.partnerAUserId as userId, userA.username as username, u.inviterUserId as inviterUserId " +
            "from UserConnectionEntity u " +
            "inner join UserEntity userA " +
            "on u.partnerAUserId = userA.userId " +
            "where u.partnerBUserId = :userId and u.status = :status")
    List<UserIdUsernameInviterUserIdProjection> findByPartnerBUserIdAndStatus(@NonNull @Param("userId") Long userId, @NonNull @Param("status") UserConnectionStatus status);
}
