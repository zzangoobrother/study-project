package com.loopers.domain.coupon

import com.loopers.domain.support.PageQuery
import com.loopers.domain.support.PageResult
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional
import java.time.ZonedDateTime

/**
 * 쿠폰 애그리거트의 유스케이스.
 *
 * 이 서비스는 회원도 주문도 모른다. 할인을 주문에 적용하는 것은 이 애그리거트의 일이 아니며,
 * 쿠폰과 주문을 잇는 책임은 OrderFacade 에만 있다. (설계 문서 7.2 장)
 *
 * 조회와 사용을 두 메서드로 나누는 이유는 조건부 UPDATE 가 영향 행 수만 돌려주고 행의 내용을
 * 주지 않기 때문이다. 할인 계산에 쿠폰 내용이 필요하므로 조회는 선택이 아니라 필수다. (설계 문서 6.3 장)
 */
@Component
class CouponService(
    private val couponRepository: CouponRepository,
    private val userCouponRepository: UserCouponRepository,
) {
    /**
     * 쿠폰을 발급한다.
     *
     * 중복 검사와 저장 사이에는 경쟁 상태가 있으며, 최종 방어선은 유니크 제약이다.
     * 경합에서 진 쪽의 DataIntegrityViolationException 은 CouponFacade 가 409 로 변환한다. (설계 문서 6.6 장)
     */
    @Transactional
    fun issue(userId: Long, couponId: Long): UserCouponModel {
        val coupon = couponRepository.findById(couponId)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $couponId] 존재하지 않는 쿠폰입니다.",
            )

        if (userCouponRepository.existsByUserIdAndCouponId(userId = userId, couponId = couponId)) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = $couponId] 이미 발급받은 쿠폰입니다.",
            )
        }

        return userCouponRepository.save(UserCouponModel.issue(userId = userId, coupon = coupon))
    }

    /** 없거나 남의 쿠폰이면 null 이다. 404 로 볼지는 유스케이스가 정한다. */
    @Transactional(readOnly = true)
    fun getUserCoupon(couponId: Long, userId: Long): UserCouponModel? {
        return userCouponRepository.findByCouponIdAndUserId(couponId = couponId, userId = userId)
    }

    /**
     * 쿠폰을 소모한다. 반환값은 "이 호출이 쿠폰을 소모했는가" 다.
     *
     * false 는 없거나·이미 썼거나·만료됐다는 뜻이며, 셋을 구분하지 않는다. (2026-08-30 설계 문서 8.2 장)
     * 최소 주문 금액 미달은 여기에 포함되지 않는다 — 호출자가 할 수 있는 일이 달라
     * OrderFacade 가 앞서 400 으로 걸러낸다. (2026-09-01 설계 문서 6.4 장)
     */
    @Transactional
    fun use(couponId: Long, userId: Long): Boolean {
        return userCouponRepository.use(couponId = couponId, userId = userId, now = ZonedDateTime.now()) == 1
    }

    @Transactional(readOnly = true)
    fun getUserCoupons(userId: Long, pageQuery: PageQuery): PageResult<UserCouponModel> {
        return userCouponRepository.findAllByUserId(userId = userId, pageQuery = pageQuery)
    }

    @Transactional
    fun register(command: CouponCommand.Register): CouponModel {
        val coupon = CouponModel.create(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiresAt = command.expiresAt,
        )
        return couponRepository.save(coupon)
    }

    /**
     * 정책을 수정한다. 더티 체킹으로 반영되므로 save 를 부르지 않는다.
     *
     * 삭제된 정책을 409 로 거부하는 것은 ProductService.change 와 같은 판단이다.
     * 없는 것(404)과 지워진 것(409)을 어드민에서는 구분한다.
     */
    @Transactional
    fun change(command: CouponCommand.Change): CouponModel {
        val coupon = couponRepository.findByIdIncludingDeleted(command.id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = ${command.id}] 존재하지 않는 쿠폰입니다.",
            )

        if (coupon.deletedAt != null) {
            throw CoreException(
                errorType = ErrorType.CONFLICT,
                customMessage = "[couponId = ${command.id}] 삭제된 쿠폰은 수정할 수 없습니다.",
            )
        }

        coupon.change(
            name = command.name,
            discountType = command.discountType,
            discountValue = command.discountValue,
            minOrderAmount = command.minOrderAmount,
            expiresAt = command.expiresAt,
        )

        return coupon
    }

    /**
     * 정책을 소프트 삭제한다. 이미 발급된 쿠폰은 건드리지 않는다. (2026-09-01 설계 문서 5.5 장)
     *
     * 연쇄가 없으므로 이 메서드는 단일 애그리거트 연산이다.
     * 상품 삭제가 좋아요를 연쇄 삭제한 것과 다른 이유는, 그 연쇄의 근거였던 목록 불일치가
     * 쿠폰에는 생기지 않기 때문이다 — 목록과 주문이 user_coupons 의 스냅샷만 읽는다.
     *
     * BaseEntity.delete 가 멱등이라 이미 삭제된 정책에 대해서도 성공한다.
     */
    @Transactional
    fun delete(id: Long) {
        val coupon = couponRepository.findByIdIncludingDeleted(id)
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[couponId = $id] 존재하지 않는 쿠폰입니다.",
            )

        coupon.delete()
    }

    /** 어드민 전용. 삭제된 정책도 200 으로 돌려주며 deletedAt 으로 구분한다. */
    @Transactional(readOnly = true)
    fun getCouponIncludingDeleted(id: Long): CouponModel? {
        return couponRepository.findByIdIncludingDeleted(id)
    }

    @Transactional(readOnly = true)
    fun getCouponsIncludingDeleted(pageQuery: PageQuery): PageResult<CouponModel> {
        return couponRepository.findAllIncludingDeleted(pageQuery)
    }

    @Transactional(readOnly = true)
    fun countIssuedByCouponIds(couponIds: List<Long>): Map<Long, Long> {
        return userCouponRepository.countIssuedByCouponIds(couponIds)
    }
}
