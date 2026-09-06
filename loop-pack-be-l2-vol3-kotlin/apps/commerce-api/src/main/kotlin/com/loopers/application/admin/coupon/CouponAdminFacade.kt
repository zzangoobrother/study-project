package com.loopers.application.admin.coupon

import com.loopers.domain.coupon.CouponCommand
import com.loopers.domain.coupon.CouponModel
import com.loopers.domain.coupon.CouponService
import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.domain.user.UserService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import java.time.ZonedDateTime

/**
 * 쿠폰 정책 어드민 유스케이스.
 *
 * 트랜잭션이 없다. 정책 삭제가 발급분을 건드리지 않아(2026-09-01 설계 문서 5.5 장) 두 애그리거트에 걸친 변경이
 * 하나도 없기 때문이다. ProductAdminFacade.delete 가 @Transactional 을 필요로 했던 것과 대비된다.
 * 쓰기 경계는 CouponService 의 @Transactional 이 소유한다.
 *
 * 인증은 AdminAuthInterceptor 가 /api-admin 하위 경로에서 처리한다.
 */
@Component
class CouponAdminFacade(
    private val couponService: CouponService,
    private val userService: UserService,
) {
    fun register(command: CouponCommand.Register): CouponAdminInfo {
        // 갓 등록한 정책의 발급 건수는 반드시 0 이다. 세러 가지 않는다.
        return CouponAdminInfo.of(couponService.register(command), issuedCount = 0)
    }

    /**
     * 어드민 정책 목록. 삭제된 정책도 포함한다.
     *
     * 발급 건수를 정책마다 세지 않고 IN 절 한 번으로 묶는다. 정책 수만큼 쿼리가 나가는 것을 막는다.
     * (2026-09-01 설계 문서 7.3 장)
     *
     * GROUP BY 결과에 발급이 0 건인 정책은 나타나지 않으므로 기본값 0 으로 채운다.
     * 이것을 빠뜨리면 발급 이력이 없는 정책의 issuedCount 가 null 이 되어 응답이 깨진다.
     */
    fun getCoupons(pageQuery: PageQuery): PageResult<CouponAdminInfo> {
        val coupons = couponService.getCouponsIncludingDeleted(pageQuery)
        val counts = couponService.countIssuedByCouponIds(coupons.content.map { it.id })

        return coupons.map { CouponAdminInfo.of(it, counts[it.id] ?: 0) }
    }

    fun getCoupon(id: Long): CouponAdminInfo {
        val coupon = couponService.getCouponIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $id] 존재하지 않는 쿠폰입니다.",
            )

        return toInfo(coupon)
    }

    fun change(command: CouponCommand.Change): CouponAdminInfo {
        return toInfo(couponService.change(command))
    }

    fun delete(id: Long) {
        couponService.delete(id)
    }

    private fun toInfo(coupon: CouponModel): CouponAdminInfo {
        val issuedCount = couponService.countIssuedByCouponIds(listOf(coupon.id))[coupon.id] ?: 0
        return CouponAdminInfo.of(coupon, issuedCount)
    }

    /**
     * 그 정책의 발급 내역.
     *
     * 정책이 없으면 404 다. 빈 목록으로 답하면 "발급이 없다" 와 "정책이 없다" 가 구분되지 않는다.
     * 삭제된 정책의 내역은 조회할 수 있다 — 삭제가 발급분을 회수하지 않으므로(2026-09-01 설계 문서 5.5 장)
     * 그 내역은 여전히 사실이다.
     *
     * 회원은 IN 절 한 번으로 채운다. 원소마다 조회하면 페이지 크기만큼 쿼리가 나간다. (2026-09-01 설계 문서 7.3 장)
     */
    fun getIssues(couponId: Long, pageQuery: PageQuery): PageResult<CouponIssueAdminInfo> {
        couponService.getCouponIncludingDeleted(couponId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 존재하지 않는 쿠폰입니다.",
            )

        val issues = couponService.getIssues(couponId = couponId, pageQuery = pageQuery)
        val users = loadUsers(issues.content.map { it.userId })
        val now = ZonedDateTime.now()

        return issues.map { CouponIssueAdminInfo.of(it, users[it.userId], now) }
    }

    /**
     * userId 를 중복 제거해 IN 절 한 번으로 조회한다. 내역이 몇 건이든 이 호출은 1 회다.
     * 탈퇴한 회원도 가져오는 이유는 OrderAdminFacade 와 같다 — 어드민에서 "탈퇴함" 과
     * "알 수 없음" 이 같은 표현으로 뭉개지면 안 된다.
     */
    private fun loadUsers(userIds: List<Long>): Map<Long, CouponIssueAdminInfo.User> {
        return userService.getUsersIncludingDeleted(userIds.distinct())
            .associate { it.id to CouponIssueAdminInfo.User.from(it) }
    }
}
