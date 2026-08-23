package com.loopers.interfaces.api.like

import com.loopers.application.like.LikeFacade
import com.loopers.domain.user.LoginId
import com.loopers.interfaces.api.ApiHeaders
import com.loopers.interfaces.api.ApiResponse
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * 상품에 딸린 좋아요 리소스.
 *
 * 목록 조회가 UserLikeV1Controller 로 갈라져 있는 것은 URL 트리가 다르기 때문이다.
 * 이 프로젝트의 컨트롤러는 클래스 레벨 RequestMapping 으로 자기 리소스 트리를 선언한다. (설계 문서 7.2 장)
 *
 * 헤더 값을 LoginId 로 감싸는 것만으로 "영문과 숫자 10자 이내" 검증이 수행된다.
 * 위반 시 LoginId 생성자가 CoreException(BAD_REQUEST) 를 던지므로 별도 검증 코드를 두지 않는다.
 *
 * 주의: 이 API 는 인증을 수행하지 않는다. 헤더 값의 형식만 검증할 뿐 요청자가 본인인지 확인하지 않으므로,
 * 로그인 ID 를 아는 누구나 타인 명의로 좋아요를 걸고 취소할 수 있다.
 * 의도된 범위 제외이며, 자격 증명 검증이 추가되기 전까지 외부에 공개해서는 안 된다. (설계 문서 11.1 장)
 */
@RestController
@RequestMapping("/api/v1/products/{productId}/likes")
class ProductLikeV1Controller(
    private val likeFacade: LikeFacade,
) : ProductLikeV1ApiSpec {
    /**
     * 201 이 아니라 200 이다. 멱등이라 "이번 요청이 실제로 행을 만들었는가" 가 요청마다 다른데,
     * 클라이언트가 그 차이로 분기해서 할 수 있는 일이 없다. (설계 문서 4.4 장)
     */
    @PostMapping
    override fun like(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.like(LoginId(loginId), productId)
        return ApiResponse.success()
    }

    @DeleteMapping
    override fun unlike(
        @RequestHeader(ApiHeaders.LOGIN_ID) loginId: String,
        @PathVariable productId: Long,
    ): ApiResponse<Any> {
        likeFacade.unlike(LoginId(loginId), productId)
        return ApiResponse.success()
    }
}
