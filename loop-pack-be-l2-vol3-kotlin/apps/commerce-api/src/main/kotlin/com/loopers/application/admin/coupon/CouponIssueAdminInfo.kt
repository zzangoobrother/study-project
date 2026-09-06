package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponStatus
import com.loopers.domain.coupon.UserCouponModel
import com.loopers.domain.user.UserModel
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 발급 내역 원소.
 *
 * 할인 조건을 담지 않는다. 정책 응답에 이미 있고, 여기서 다시 내보내면 정책이 수정된 뒤
 * 두 값이 어긋나 보인다. 어긋나는 것이 사실이지만(2026-09-01 설계 문서 11.2 장) 이 목록의 목적은
 * "누가 언제 받아 갔는가" 다. (2026-09-01 설계 문서 4.4 장)
 *
 * user 가 nullable 인 이유는 OrderAdminInfo 와 같다. 탈퇴 회원도 getUsersIncludingDeleted 로
 * 채우므로 null 은 정말로 회원 행이 사라진 경우뿐이다 — FK 가 없어 이론상 가능하다.
 */
data class CouponIssueAdminInfo(
    val user: User?,
    val status: CouponStatus,
    val issuedAt: ZonedDateTime,
    val usedAt: ZonedDateTime?,
) {
    /**
     * 노출하는 회원 정보는 id 와 loginId 뿐이다. 이름·이메일·생년월일 같은 개인정보는 담지 않는다.
     * LoginId 값 객체가 아니라 원시 문자열로 펼치는 이유는 OrderAdminInfo.User 와 같다.
     */
    data class User(
        val id: Long,
        val loginId: String,
    ) {
        companion object {
            fun from(model: UserModel): User = User(id = model.id, loginId = model.loginId.value)
        }
    }

    companion object {
        /**
         * now 를 인자로 받는 이유는 CouponInfo.of 와 같다 — 목록의 모든 원소가 같은 순간을
         * 기준으로 판정되어야 만료 경계에서 서로 모순되는 조합이 나오지 않는다.
         */
        fun of(model: UserCouponModel, user: User?, now: ZonedDateTime): CouponIssueAdminInfo =
            CouponIssueAdminInfo(
                user = user,
                status = model.statusAt(now),
                issuedAt = model.createdAt,
                usedAt = model.usedAt,
            )
    }
}
