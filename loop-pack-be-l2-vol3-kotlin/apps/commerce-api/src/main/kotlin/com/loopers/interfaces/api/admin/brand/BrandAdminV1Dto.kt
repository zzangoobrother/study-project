package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.BrandAdminInfo
import java.time.ZonedDateTime

class BrandAdminV1Dto {
    /**
     * 어드민 브랜드 응답. 목록의 원소와 단건 조회 응답이 같은 타입이다.
     *
     * deleted 와 deletedAt 을 함께 두는 이유는 JacksonConfig 의 NON_NULL 설정 때문이다.
     * 살아 있는 브랜드의 응답에서는 deletedAt 키가 사라지므로, 항상 존재하는 boolean 이 있어야
     * 클라이언트가 "삭제되지 않음" 과 "서버가 그 필드를 안 보냄" 을 구분할 수 있다.
     *
     * 타임스탬프를 노출하는 이유는 목록이 최신순으로 정렬되기 때문이다.
     * 정렬 기준 값이 응답에 없으면 클라이언트가 정렬 결과를 확인할 방법이 없다.
     */
    data class BrandResponse(
        val id: Long,
        val name: String,
        val description: String,
        val deleted: Boolean,
        val deletedAt: ZonedDateTime?,
        val createdAt: ZonedDateTime,
        val updatedAt: ZonedDateTime,
    ) {
        companion object {
            fun from(info: BrandAdminInfo): BrandResponse {
                return BrandResponse(
                    id = info.id,
                    name = info.name.value,
                    description = info.description.value,
                    deleted = info.deleted,
                    deletedAt = info.deletedAt,
                    createdAt = info.createdAt,
                    updatedAt = info.updatedAt,
                )
            }
        }
    }
}
