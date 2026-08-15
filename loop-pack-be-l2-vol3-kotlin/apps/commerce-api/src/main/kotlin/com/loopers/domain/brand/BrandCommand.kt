package com.loopers.domain.brand

/**
 * 브랜드 쓰기 유스케이스의 입력.
 *
 * 값 객체만 담으므로 이 객체가 만들어졌다는 것 자체가 포맷 검증 통과를 의미한다.
 * String → 값 객체 변환은 인터페이스 계층의 DTO 가 하고, 그 과정에서 400 이 던져진다.
 */
class BrandCommand {
    data class Register(
        val name: BrandName,
        val description: BrandDescription,
    )

    data class Change(
        val id: Long,
        val name: BrandName,
        val description: BrandDescription,
    )
}
