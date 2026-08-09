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

    /**
     * 비밀번호를 교체한다.
     *
     * "바꿔도 되는가"(기존 비밀번호 일치)와 "무엇으로 바꿀 수 있는가"(기존과 다를 것, 생년월일 불포함)가
     * 모두 이 애그리거트의 상태(password, birthDate)에 의존하므로 판정을 여기서 한다.
     *
     * 검사 순서는 인증(401) → 정책(400) 이다.
     * 인증되지 않은 요청자에게 새 비밀번호의 정책 위반 여부를 알려주지 않는다.
     */
    fun changePassword(
        currentPassword: RawPassword,
        newPassword: RawPassword,
        passwordEncoder: PasswordEncoder,
    ) {
        if (!passwordEncoder.matches(currentPassword, password)) {
            throw CoreException(ErrorType.UNAUTHORIZED, INVALID_CREDENTIAL_MESSAGE)
        }

        // encode() 는 호출마다 새 salt 를 뽑아 같은 평문도 다른 결과를 낸다.
        // 따라서 encode 결과 비교로는 판정할 수 없고 반드시 matches 를 써야 한다.
        if (passwordEncoder.matches(newPassword, password)) {
            throw CoreException(ErrorType.BAD_REQUEST, "새 비밀번호는 기존 비밀번호와 달라야 합니다.")
        }

        validateBirthDateNotIncluded(newPassword, birthDate)

        password = passwordEncoder.encode(newPassword)
    }

    companion object {
        /**
         * 자격 증명 검증 실패 시의 단일 문구.
         *
         * 미가입 / 소프트 삭제 / 비밀번호 불일치를 구분해 알려주지 않기 위해 UserService 도 이 상수를 참조한다.
         * 응답 차이로 로그인 ID 의 존재 여부를 유추할 수 있는 경로를 남기지 않는다.
         */
        const val INVALID_CREDENTIAL_MESSAGE = "로그인 ID 또는 비밀번호가 올바르지 않습니다."

        /** 비밀번호에 포함될 수 없는 생년월일 표기. 연도/월일 단독은 오탐이 커 대상에서 제외한다. */
        private val FORBIDDEN_BIRTH_DATE_FORMATS = listOf(
            DateTimeFormatter.ofPattern("yyyyMMdd"),
            DateTimeFormatter.ofPattern("yyMMdd"),
        )

        /**
         * 단일 값으로 판정할 수 없는 규칙이라 애그리거트 루트가 소유한다.
         * 가입(create)과 비밀번호 변경(changePassword)이 같은 규칙을 쓰도록 한 곳에 둔다.
         */
        private fun validateBirthDateNotIncluded(rawPassword: RawPassword, birthDate: BirthDate) {
            if (FORBIDDEN_BIRTH_DATE_FORMATS.any { rawPassword.contains(birthDate.value.format(it)) }) {
                throw CoreException(ErrorType.BAD_REQUEST, "비밀번호에 생년월일을 포함할 수 없습니다.")
            }
        }

        fun create(
            loginId: LoginId,
            rawPassword: RawPassword,
            name: UserName,
            birthDate: BirthDate,
            email: Email,
            passwordEncoder: PasswordEncoder,
        ): UserModel {
            validateBirthDateNotIncluded(rawPassword, birthDate)

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
