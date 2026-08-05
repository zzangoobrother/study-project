package com.loopers.domain.user

import com.loopers.domain.BaseEntity
import com.loopers.support.error.CoreException
import com.loopers.support.error.ErrorType
import jakarta.persistence.AttributeOverride
import jakarta.persistence.Column
import jakarta.persistence.Embedded
import jakarta.persistence.Entity
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.format.DateTimeFormatter

/**
 * 회원 엔티티.
 *
 * 필드별 검증은 각 값 객체가 소유하므로, 이 클래스에는 여러 값에 걸친 규칙만 남는다.
 * 평문 비밀번호가 저장되는 경로는 [EncodedPassword] 타입으로 차단된다.
 */
@Entity
@Table(
    name = "users",
    uniqueConstraints = [UniqueConstraint(name = "uk_users_login_id", columnNames = ["login_id"])],
)
class UserModel private constructor(
    loginId: LoginId,
    password: EncodedPassword,
    name: UserName,
    birthDate: BirthDate,
    email: Email,
) : BaseEntity() {
    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "login_id", nullable = false, length = 10))
    var loginId: LoginId = loginId
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "password", nullable = false))
    var password: EncodedPassword = password
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "name", nullable = false, length = 20))
    var name: UserName = name
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "birth_date", nullable = false))
    var birthDate: BirthDate = birthDate
        protected set

    @Embedded
    @AttributeOverride(name = "value", column = Column(name = "email", nullable = false, length = 254))
    var email: Email = email
        protected set

    companion object {
        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        fun create(
            loginId: LoginId,
            rawPassword: RawPassword,
            name: UserName,
            birthDate: BirthDate,
            email: Email,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            // 단일 값으로 판정할 수 없는 규칙만 애그리거트 루트가 소유한다.
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.value.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }

            return UserModel(
                loginId = loginId,
                password = passwordEncoder.encode(rawPassword),
                name = name,
                birthDate = birthDate,
                email = email,
            )
        }
    }
}
