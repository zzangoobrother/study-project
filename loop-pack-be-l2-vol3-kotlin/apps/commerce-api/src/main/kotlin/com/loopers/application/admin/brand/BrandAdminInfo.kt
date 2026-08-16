package com.loopers.application.admin.brand

import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandModel
import com.loopers.domain.brand.BrandName
import java.time.ZonedDateTime

/**
 * 어드민 계층 밖으로 전달되는 브랜드 정보.
 *
 * 공개 API 의 BrandInfo 와 따로 두는 이유는 필드가 다르기 때문이다.
 * 삭제 여부와 타임스탬프는 어드민에만 필요하며, 그것을 BrandInfo 에 추가하면
 * 아무도 쓰지 않는 필드가 공개 응답 경로로 흘러간다.
 *
 * 값 객체를 그대로 들고 다니고 String 변환은 DTO 가 한다. 공개 API 와 같은 규약이다.
 */
data class BrandAdminInfo(
    val id: Long,
    val name: BrandName,
    val description: BrandDescription,
    val deletedAt: ZonedDateTime?,
    val createdAt: ZonedDateTime,
    val updatedAt: ZonedDateTime,
) {
    /**
     * deletedAt 만으로 삭제 여부를 표현하지 않는 이유는 응답 직렬화 때문이다.
     * JacksonConfig 가 NON_NULL 을 전역으로 켜서 살아 있는 리소스의 응답에는 deletedAt 키 자체가 사라진다.
     * 항상 존재하는 boolean 이 "삭제되지 않음" 과 "서버가 그 필드를 안 보냄" 의 모호함을 없앤다.
     */
    val deleted: Boolean get() = deletedAt != null

    companion object {
        fun from(model: BrandModel): BrandAdminInfo {
            return BrandAdminInfo(
                id = model.id,
                name = model.name,
                description = model.description,
                deletedAt = model.deletedAt,
                createdAt = model.createdAt,
                updatedAt = model.updatedAt,
            )
        }
    }
}
