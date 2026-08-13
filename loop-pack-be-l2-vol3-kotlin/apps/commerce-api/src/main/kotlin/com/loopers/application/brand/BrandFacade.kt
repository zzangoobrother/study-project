package com.loopers.application.brand

import com.loopers.domain.brand.BrandService
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import org.springframework.stereotype.Component

@Component
class BrandFacade(
    private val brandService: BrandService,
) {
    /**
     * 브랜드 정보를 조회한다.
     *
     * "브랜드가 없음" 을 404 로 볼지 결정하는 것은 유스케이스의 책임이므로 이 계층에서 변환한다.
     * 미등록과 소프트 삭제를 구분하지 않는다. 어느 쪽이든 클라이언트가 할 수 있는 일이 같다.
     */
    fun getBrand(id: Long): BrandInfo {
        return brandService.getBrand(id)
            ?.let { BrandInfo.from(it) }
            ?: throw CoreException(
                errorType = ErrorType.NOT_FOUND,
                customMessage = "[brandId = $id] 존재하지 않는 브랜드입니다.",
            )
    }
}
