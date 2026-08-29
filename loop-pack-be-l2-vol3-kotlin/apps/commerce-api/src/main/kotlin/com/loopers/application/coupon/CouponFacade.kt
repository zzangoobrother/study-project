package com.loopers.application.coupon

import com.loopers.domain.coupon.CouponService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.domain.user.LoginId
import com.loopers.domain.user.UserModel
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 회원과 쿠폰 두 애그리거트를 조합하는 유스케이스.
 *
 * LikeFacade 와 달리 평범한 @Transactional 을 쓴다. (설계 문서 6.6 장)
 * 좋아요는 경합 예외를 삼킨 뒤 성공으로 응답해야 했는데, @Transactional 안에서 잡으면 트랜잭션이
 * 이미 rollback-only 로 마킹되어 커밋할 수 없었다. 발급은 예외를 CONFLICT 로 바꿔 다시 던질 뿐이라
 * 롤백이 오히려 정답이며, rollback-only 마킹이 문제가 되지 않는다.
 */
@Component
class CouponFacade(
    private val userService: UserService,
    private val couponService: CouponService,
) {
    /**
     * BaseEntity 가 IDENTITY 전략이라 save() 시점에 INSERT 가 즉시 나간다.
     * 유니크 제약 위반이 커밋이 아니라 이 try 블록 안에서 드러나는 이유다.
     */
    @Transactional
    fun issue(loginId: LoginId, couponId: Long): CouponInfo {
        val user = getUserOrThrow(loginId)

        val issued = try {
            couponService.issue(userId = user.id, couponId = couponId)
        } catch (e: DataIntegrityViolationException) {
            // 동시 발급 경합에서 진 쪽이다. 서비스의 중복 검사와 저장 사이의 틈을 유니크 제약이 막았다.
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = $couponId] 이미 발급받은 쿠폰입니다.",
            )
        }

        return CouponInfo.of(issued, ZonedDateTime.now())
    }

    /**
     * 기준 시각을 한 번만 읽어 모든 원소에 같은 값을 넘긴다.
     * 원소마다 now() 를 부르면 만료 경계에 걸린 쿠폰들의 상태가 서로 모순될 수 있다.
     */
    @Transactional(readOnly = true)
    fun getUserCoupons(loginId: LoginId, pageQuery: PageQuery): PageResult<CouponInfo> {
        val user = getUserOrThrow(loginId)
        val now = ZonedDateTime.now()

        return couponService.getUserCoupons(userId = user.id, pageQuery = pageQuery)
            .map { CouponInfo.of(it, now) }
    }

    private fun getUserOrThrow(loginId: LoginId): UserModel =
        userService.getUser(loginId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[loginId = ${loginId.value}] 존재하지 않는 회원입니다.",
            )
}
