package com.loopers.interfaces.api.admin.brand

import com.loopers.application.admin.brand.BrandAdminInfo
import com.loopers.domain.brand.BrandCommand
import com.loopers.domain.brand.BrandDescription
import com.loopers.domain.brand.BrandName
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

    /**
     * 브랜드 등록 요청.
     *
     * name 이 non-null String 이라 필드가 없으면 Jackson 이 MismatchedInputException 을 던지고
     * ApiControllerAdvice 가 400 으로 변환한다. @Valid 나 if 검증문을 두지 않는 이유다.
     * 값 자체의 규칙(빈 값, 길이)은 BrandName 생성자가 소유한다.
     */
    data class RegisterRequest(
        val name: String,
        val description: String? = null,
    ) {
        fun toCommand(): BrandCommand.Register = BrandCommand.Register(
            name = BrandName(name),
            description = description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY,
        )
    }

    /**
     * 브랜드 수정 요청. PUT 이므로 전체 교체다.
     *
     * description 을 생략하면 기존 값이 유지되는 것이 아니라 BrandDescription.EMPTY 로 덮인다.
     * 부분 수정이 필요해지면 PATCH 를 따로 만들지, 이 DTO 에 "null 이면 유지" 규칙을 넣지 않는다.
     */
    data class ChangeRequest(
        val name: String,
        val description: String? = null,
    ) {
        fun toCommand(id: Long): BrandCommand.Change = BrandCommand.Change(
            id = id,
            name = BrandName(name),
            description = description?.let { BrandDescription(it) } ?: BrandDescription.EMPTY,
        )
    }
}
